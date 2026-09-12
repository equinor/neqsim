"""
new_task.py - Create a new task-solving workspace and task folders.

This script lives in devtools/ (tracked in git) and is always available
after cloning. It auto-creates the task_solve/ folder structure on first run.

Usage:
    neqsim new-task "JT cooling for rich gas"
    neqsim new-task "TEG dehydration sizing" --type B
    neqsim new-task "hydrate formation temperature" --type A --author "Your Name"
    neqsim new-task "task title" --prompt "verbatim user request"
    neqsim new-task "task title" --prompt-file path/to/request.txt
    neqsim new-task "field study" --scale comprehensive --report-depth detailed
    neqsim new-task "field study" --notebooks "01_basis.ipynb,02_model.ipynb"
    neqsim new-task "field study" --intake-pause always
    neqsim new-task "field study" --config-file study_config.yaml
    neqsim new-task --setup              # just create task_solve/ without a task
    neqsim new-task --list               # list existing tasks
    neqsim new-task --set-default-folder "D:/Engineering Tasks"
    neqsim new-task --set-default-folder cwd   # follow the terminal folder
    neqsim new-task --show-task-root     # print the resolved destination
    neqsim new-task "task title" --task-root "D:/One-off Studies"
    neqsim new-task "task title" --task-root .    # into the terminal folder
    neqsim new-task --reset-default-folder
    neqsim new-task --set-report-template "C:/…/company template.docx"
    neqsim new-task --show-report-template
    neqsim new-task --reset-report-template
    neqsim new-task --set-document-root "C:/…/Engineering Documents"
    neqsim new-task --show-document-root
    neqsim new-task --reset-document-root

Destination precedence: --task-root, NEQSIM_TASK_ROOT, saved user default,
then <repository>/task_solve. Settings: ~/.neqsim/task_defaults.json.
The short top-level equivalents are `neqsim --set-task-root PATH`,
`neqsim --show-task-root`, and `neqsim --reset-task-root`.

Word reports are built from the template resolved as: generate_report.py
--template PATH, NEQSIM_REPORT_TEMPLATE, then the saved `report_template`
setting. The short top-level equivalents are `neqsim --set-report-template
PATH`, `neqsim --show-report-template`, and `neqsim --reset-report-template`.

The document root is the folder agents read source documents from, including
every subfolder. It resolves as: explicit path, NEQSIM_DOCUMENT_ROOT, then the
saved `document_root` setting. The short top-level equivalents are `neqsim
--set-document-root PATH`, `neqsim --show-document-root`, `neqsim
--reset-document-root`, and `neqsim documents [PATTERN]` to search it.
"""
import argparse
import os
import json
import shutil
import sys
from datetime import date


# ── Paths ────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
TASK_SOLVE_DIR = os.path.join(PROJECT_ROOT, "task_solve")
TEMPLATE_DIR = os.path.join(TASK_SOLVE_DIR, "TASK_TEMPLATE")
CWD_TASK_ROOT = "."
CWD_ALIASES = ("cwd", "here", ".")
REPORT_TEMPLATE_EXTENSIONS = (".docx", ".dotx")

# Recorded by the tooling, not chosen by the user: never keeps the file alive.
BOOKKEEPING_SETTINGS = ("project_root",)


def task_defaults_path():
    """Return the user-level task destination configuration file."""
    return os.path.expanduser("~/.neqsim/task_defaults.json")


def read_task_defaults():
    """Return the saved user settings, or an empty mapping when unset."""
    path = task_defaults_path()
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8-sig") as source:
        settings = json.load(source)
    if not isinstance(settings, dict):
        raise ValueError("{} must contain a JSON object".format(path))
    return settings


def _write_task_defaults(settings):
    """Persist user settings, removing the file once no setting remains."""
    path = task_defaults_path()
    if not any(key not in BOOKKEEPING_SETTINGS for key in settings):
        if os.path.exists(path):
            os.remove(path)
        return
    _write_file(path, json.dumps(settings, indent=2) + "\n")


def _normalize_task_root(task_root):
    """Map follow-the-terminal aliases onto the current working directory."""
    return CWD_TASK_ROOT if task_root.strip().lower() in CWD_ALIASES else task_root


def resolve_task_root(task_root=None):
    """Resolve explicit, environment, saved-user, then repository task root."""
    selected = task_root or os.environ.get("NEQSIM_TASK_ROOT")
    if not selected:
        selected = read_task_defaults().get("task_root")
    if selected is not None and (not isinstance(selected, str) or not selected.strip()):
        raise ValueError("Task root must be a non-empty path string")
    selected = _normalize_task_root(selected) if selected else TASK_SOLVE_DIR
    return os.path.abspath(os.path.expandvars(os.path.expanduser(selected)))


def save_default_task_root(task_root):
    """Persist an absolute task root, or '.' to follow the terminal folder."""
    if not task_root or not task_root.strip():
        raise ValueError("Task root must be a non-empty path string")
    stored = _normalize_task_root(task_root)
    if stored != CWD_TASK_ROOT:
        stored = resolve_task_root(task_root)
    settings = read_task_defaults()
    settings["task_root"] = stored
    _write_task_defaults(settings)
    return stored


def clear_default_task_root():
    """Remove the saved task root, keeping other settings intact."""
    settings = read_task_defaults()
    removed = settings.pop("task_root", None) is not None
    _write_task_defaults(settings)
    return removed


# Task folders carry a launcher, not a copy, of this generator.
VENDORED_GENERATOR_REL = "step3_report/generate_report.py"


def canonical_generator_path():
    """Return the absolute path of the one report generator everyone runs."""
    return os.path.join(SCRIPT_DIR, "task_template", "step3_report",
                        "generate_report.py")


def save_project_root():
    """Record this checkout so task folders elsewhere can find the tooling."""
    try:
        settings = read_task_defaults()
    except (OSError, ValueError):
        return None
    if settings.get("project_root") == PROJECT_ROOT:
        return PROJECT_ROOT
    settings["project_root"] = PROJECT_ROOT
    try:
        _write_task_defaults(settings)
    except OSError:
        return None
    return PROJECT_ROOT


def resolve_report_template(report_template=None):
    """Resolve explicit, environment, then saved-user Word report template.

    Returns an absolute path to an existing .docx/.dotx file, or None when no
    template is configured. Raises ValueError for a configured but unusable
    template so the branding failure is reported instead of silently dropped.

    Parameters
    ----------
    report_template : str or None
        Explicit template path that overrides environment and saved settings.

    Returns
    -------
    str or None
        Absolute template path, or None when the built-in styling applies.

    Raises
    ------
    ValueError
        If the resolved template is blank, not a Word file, or missing.
    """
    selected = report_template or os.environ.get("NEQSIM_REPORT_TEMPLATE")
    if not selected:
        selected = read_task_defaults().get("report_template")
    if selected is None:
        return None
    if not isinstance(selected, str) or not selected.strip():
        raise ValueError("Report template must be a non-empty path to a .docx or .dotx file")
    path = os.path.abspath(os.path.expandvars(os.path.expanduser(selected)))
    if os.path.splitext(path)[1].lower() not in REPORT_TEMPLATE_EXTENSIONS:
        raise ValueError("Report template must be a .docx or .dotx file: {}".format(path))
    if not os.path.isfile(path):
        raise ValueError("Report template not found: {}".format(path))
    return path


def save_default_report_template(report_template):
    """Persist the Word template every generated report should be built from."""
    if not report_template or not report_template.strip():
        raise ValueError("Report template must be a non-empty path to a .docx or .dotx file")
    stored = resolve_report_template(report_template)
    settings = read_task_defaults()
    settings["report_template"] = stored
    _write_task_defaults(settings)
    return stored


def clear_default_report_template():
    """Remove the saved report template, keeping other settings intact."""
    settings = read_task_defaults()
    removed = settings.pop("report_template", None) is not None
    _write_task_defaults(settings)
    return removed


def resolve_document_root(document_root=None):
    """Resolve explicit, environment, then saved-user source-document folder.

    The document root is the folder agents read source documents from, and every
    subfolder below it is in scope. Returns None when no folder is configured.
    A configured but missing folder raises instead of silently resolving to
    nothing, so the agent reports the blocker rather than working document-free.

    Parameters
    ----------
    document_root : str or None
        Explicit folder that overrides environment and saved settings.

    Returns
    -------
    str or None
        Absolute folder path, or None when no document root is configured.

    Raises
    ------
    ValueError
        If the resolved document root is blank or is not an existing folder.
    """
    selected = document_root or os.environ.get("NEQSIM_DOCUMENT_ROOT")
    if not selected:
        selected = read_task_defaults().get("document_root")
    if selected is None:
        return None
    if not isinstance(selected, str) or not selected.strip():
        raise ValueError("Document root must be a non-empty folder path")
    path = os.path.abspath(os.path.expandvars(os.path.expanduser(selected)))
    if not os.path.isdir(path):
        raise ValueError("Document root folder not found: {}".format(path))
    return path


def save_default_document_root(document_root):
    """Persist the folder every task reads source documents from."""
    if not document_root or not document_root.strip():
        raise ValueError("Document root must be a non-empty folder path")
    stored = resolve_document_root(document_root)
    settings = read_task_defaults()
    settings["document_root"] = stored
    _write_task_defaults(settings)
    return stored


def clear_default_document_root():
    """Remove the saved document root, keeping other settings intact."""
    settings = read_task_defaults()
    removed = settings.pop("document_root", None) is not None
    _write_task_defaults(settings)
    return removed


def find_documents(pattern="", document_root=None, limit=0):
    """List documents under the configured document root and all subfolders.

    Parameters
    ----------
    pattern : str
        Case-insensitive substring matched against the path relative to the
        document root. Empty lists every file.
    document_root : str or None
        Explicit folder that overrides environment and saved settings.
    limit : int
        Maximum number of matches to return; 0 returns all of them.

    Returns
    -------
    list of str
        Absolute file paths, sorted, from the root and every subfolder.
    """
    root = resolve_document_root(document_root)
    if root is None:
        raise ValueError(
            "No document root configured. Set one: neqsim --set-document-root \"PATH\"")
    needle = (pattern or "").strip().lower()
    matches = []
    for folder, subfolders, files in os.walk(root):
        subfolders[:] = sorted(name for name in subfolders if not name.startswith("."))
        for name in sorted(files):
            if name.startswith("."):
                continue
            path = os.path.join(folder, name)
            if needle and needle not in os.path.relpath(path, root).lower():
                continue
            matches.append(path)
            if limit and len(matches) >= limit:
                return matches
    return matches

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
> `neqsim new-task "your task"` and follow the prompts in the
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

1. Type: `@solve.task add JT coefficient method` (or run `neqsim new-task` for manual control)
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

1. Run the setup: `neqsim new-task "your task" --type B`
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
- `neqsim new-task` — creates task folders
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

**Providing literature papers and reference documents:**

If you have PDF papers, standards documents, lab reports, or other background
material, place them in the `step1_scope_and_research/references/` folder.
Then summarise their key contributions in `notes.md` under "Literature &
Reference Documents". See `references/README.md` for naming conventions and
tips on how the AI can use these files.

Use **either** Google NotebookLM or Copilot — or both:

**Option A: Google NotebookLM** (best for deep literature review)
- Upload PDFs from `step1_scope_and_research/references/` to NotebookLM
- Ask questions across all your sources at once
- Get cited answers with references back to source pages
- Paste findings into `notes.md`

**Option B: GitHub Copilot in VS Code** (best for code-adjacent research)
- Open Copilot Chat and ask research questions directly
- Copilot can search the web, read repo docs, and summarise findings
- For PDFs, extract pages as images using `devtools/pdf_to_figures.py`:
  ```bash
  python devtools/pdf_to_figures.py step1_scope_and_research/references/ --outdir figures/
  ```
  Then use `view_image` on the extracted PNGs to read diagrams, charts, and tables

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
7. **Create a benchmark validation notebook** (`XX_benchmark_validation.ipynb`)
   comparing NeqSim results against independent reference data (NIST, textbook,
   published cases, industry benchmarks). Include at least 3 data points, a
   parity/deviation plot, and save `benchmark_validation` to `results.json`.

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
- [ ] Reference documents placed in `step1_scope_and_research/references/`
- [ ] Reference data collected
- [ ] Key sources documented in `step1_scope_and_research/notes.md`

**Providing literature papers and reference documents:**

If you have PDF papers, standards documents, lab reports, or other background
material, place them in the `step1_scope_and_research/references/` folder.
Then summarise their key contributions in `notes.md` under "Literature &
Reference Documents". See `references/README.md` for naming conventions and
tips on how the AI can use these files.

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
- [ ] `WORK_RECORD.md` generated and its narrative blocks filled
- [ ] All required deliverables from task spec produced
- [ ] Task logged in `docs/development/TASK_LOG.md`

**To generate reports:**

```powershell
pip install python-docx matplotlib    # one-time setup
python step3_report/generate_report.py
```

The report files are named after the report title (from `study.title` in
`study_config.yaml`, or `--title`), e.g. `Hydrate_margin_export_line.docx`, so a
deliverable is identifiable outside its task folder. The Word file is built from
the template saved with
`neqsim --set-report-template "PATH"` (check it with `neqsim --show-report-template`),
so it carries your organisation's styles, fonts, headers, and footers. Override
for one run with `--template "PATH"`, or ignore it with `--no-template`.

**The work record is generated with the report** (`step3_report/WORK_RECORD.md`).
Rebuild it alone with:

```powershell
neqsim work-record .          # run from the task folder
neqsim work-record . --check  # verify it is filled in
```

The report says what the answer is; the work record says how it was produced —
every script and notebook with its purpose and outputs, every source system and
collected document, the cached data files, an annotated folder map, and the
commands to reproduce the study. Write the `background`, `method`, and
`limitations` NARRATIVE blocks by hand; they are preserved on regeneration.
Declaring `analysis.scripts` and `inputs.data_sources` in `study_config.yaml`
is what turns the auto-built sections into a real method and data record.

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

## Saving Results for the Report (results.json)

The report generator (`step3_report/generate_report.py`) auto-reads a `results.json`
file from the task root. Add a cell at the end of your notebook to save results:

```python
import json, os, pathlib

# Resolve task directory from the notebook's own location
# (os.getcwd() is unreliable in VS Code notebooks — it returns workspace root)
NOTEBOOK_DIR = pathlib.Path(globals().get(
    "__vsc_ipynb_file__", os.path.abspath("step2_analysis/notebook.ipynb")
)).resolve().parent
TASK_DIR = NOTEBOOK_DIR.parent
FIGURES_DIR = TASK_DIR / "figures"
FIGURES_DIR.mkdir(exist_ok=True)

results = {
    "key_results": {
        # Add your key numerical results here (units in the key name)
        "outlet_temperature_C": 25.3,
        "pressure_drop_bar": 5.2,
        "power_kW": 1250.0,
    },
    "validation": {
        # Each check: True = pass, False = fail, or a numeric value
        "mass_balance_error_pct": 0.01,
        "energy_balance_error_pct": 0.5,
        "temperature_in_range": True,
        "pressure_positive": True,
        "acceptance_criteria_met": True,
    },
    "approach": "Used SRK EOS with classic mixing rule. Process: ...",
    "conclusions": "The analysis shows that ...",
    # Optional: custom figure captions (map filename -> caption text)
    "figure_captions": {
        # "my_plot.png": "Temperature and pressure profiles during simulation",
    },
    # Optional: key equations used in the analysis (rendered in reports)
    "equations": [
        # {"label": "Energy Balance", "latex": "Q = m C_p \\\\Delta T"},
    ],
    # Optional: custom tables (rendered in both Word and HTML)
    "tables": [
        # {"title": "Sensitivity Analysis",
        #  "headers": ["Parameter", "Base Case", "Low", "High"],
        #  "rows": [["Pressure (bar)", 60.0, 40.0, 80.0],
        #           ["Temperature (C)", 25.0, 15.0, 35.0]]}
    ],
    # Optional: references (rendered as numbered list in References section)
    "references": [
        # {"id": "Smith2019", "text": "Smith, J. (2019). CNG Tank Thermal Analysis. J. Energy Storage, 25, 100-115."},
        # {"id": "API521", "text": "API 521, 7th Edition (2020). Pressure-Relieving and Depressuring Systems."},
    ],
}

results_path = str(TASK_DIR / "results.json")
with open(results_path, "w") as f:
    json.dump(results, f, indent=2)
print(f"Results saved to {results_path}")
```

**Saving figures** — use `FIGURES_DIR` (set above) so plots end up in the right place:

```python
import matplotlib.pyplot as plt

fig, ax = plt.subplots()
ax.plot(x, y)
ax.set_xlabel("X Label")
ax.set_ylabel("Y Label")
fig.savefig(str(FIGURES_DIR / "my_plot.png"), dpi=150, bbox_inches="tight")
plt.show()
```

When you run `python step3_report/generate_report.py`, the Results and Validation
sections are auto-populated from this file. Figures in `figures/` are also embedded.
Equations from results.json are rendered with KaTeX in HTML and as native OMML
equations in Word (editable, scalable, matching document font). Falls back to
matplotlib images if latex2mathml/lxml/MML2OMML.XSL are unavailable.

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

## Literature & Reference Documents

Place PDF papers, standards, and technical reports in the `references/` folder
next to this file. Then summarise each document's key contributions here.

**How to add a reference:**
1. Copy the PDF/document to `step1_scope_and_research/references/`
2. Add a row to the Sources table above
3. Write a brief summary below noting the relevant equations, data, or design rules

### Paper/Document Summaries

<!--
For each reference document, add a subsection like this:

### Smith (2019) — CNG Tank Thermal Analysis
- **File:** `references/Smith_2019_CNG_Tank_Thermal_Analysis.pdf`
- **Relevance:** Provides heat transfer correlations for compressed gas filling
- **Key equations:** Eq. 12 — convective HTC = 15-25 W/m2K for turbulent fill
- **Key data:** Table 3 — experimental fill temperatures vs. time
- **Limitations:** Only covers Type III tanks, ambient temperature 20C
-->

## Background

[Summary of the engineering context]

## Key Data / Correlations

[Reference values, correlations, experimental data]

## Open Questions

- [ ]
"""

REFERENCES_README = """# References Folder

Place literature papers, standards documents, and other reference material here.

## Where source documents come from

If the user has configured a document root, it is recorded as `inputs.document_root`
in `study_config.yaml` and printed by `neqsim --show-document-root`. That folder
**and all its subfolders** are the source library for this task:

```bash
neqsim --show-document-root       # may be unset - then there is no library
neqsim documents "API 521"        # recursive search when one is configured
```

The setting is optional. When it is unset, work from the documents the user
supplies directly and record the missing evidence as a data gap. When it is set,
search it before reporting a standard, datasheet, or drawing as unavailable. The
library is read-only: copy the documents this task actually uses into a
per-source subfolder here (`stid/`, `vendor/`, `literature/`, `manual/`, ...) so
the task folder stays self-contained.

## What to put in this folder

- **PDF papers** -- journal articles, conference papers, technical reports
- **Standards excerpts** -- relevant sections from ASME, API, DNV, ISO, NORSOK, etc.
- **Company documents** -- TR documents, design basis, operating philosophy
- **Data sheets** -- equipment data sheets, material certificates
- **Lab reports** -- PVT reports, fluid analysis, corrosion test results

## How the AI uses these files

1. **PDF figure extraction (PREFERRED for visual content):** Use `devtools/pdf_to_figures.py`
   to convert PDF pages to PNG images, then use `view_image` to analyze engineering
   drawings, P&IDs, charts, data tables, and compressor maps:
   ```bash
   python devtools/pdf_to_figures.py step1_scope_and_research/references/ --outdir figures/
   ```
   This is the fastest way to make PDF content available for AI analysis.

2. **Google NotebookLM (recommended for deep literature review):** Upload the PDFs from this
   folder to NotebookLM. It can read, cross-reference, and cite multiple
   documents at once. Ask it targeted questions and paste the answers into
   `notes.md`.

3. **VS Code Copilot Chat:** Copilot can read text-based files (`.txt`, `.md`,
   `.csv`) placed here. For PDFs, first extract pages as images using
   `pdf_to_figures.py`, then use `view_image` to read the content.

4. **Manual notes:** Read the papers yourself and capture key equations,
   data points, and design rules in `notes.md` under the "Literature &
   Reference Documents" section.

## Naming convention

Use descriptive filenames that include author/org and year:

```
Smith_2019_CNG_Tank_Thermal_Analysis.pdf
API_521_6th_Ed_Relief_Systems.pdf
DNV-ST-F101_2021_Submarine_Pipelines.pdf
OperatorA_TR2000_Pressure_Vessel_Design.pdf
Lab_Report_Fluid_Analysis_2024.pdf
```

## Documenting references

After placing files here, add each to the Sources table in `notes.md`:

| # | Source | Type | Key Finding |
|---|--------|------|-------------|
| 1 | Smith (2019) -- see `references/Smith_2019_CNG_Tank_...pdf` | Paper | Heat transfer coefficient 15-25 W/m2K |
| 2 | API 521 6th Ed | Standard | Relief sizing per Section 5.4 |

And add structured entries to the `references` list in `results.json` so they
appear in the final report (see `results.json` schema in AGENTS.md).
"""

TASK_SPEC = """# Task Specification

This file defines the scope and requirements that guide the analysis in Step 2.
Fill this in before starting any simulation work.

## Objective

[State, in 2-4 sentences, what was asked and what the study must deliver. This
text is quoted at the top of the report under "Task", so write it as the task
statement a reader should see first.]

## Background

[Why the task was raised: the operational trigger, the decision it feeds, and any
constraint that frames it. Delete this section if the objective is
self-contained.]

## Applicable Standards

List the codes, standards, and company requirements that govern this task.

| Standard | Scope | Key Requirements |
|----------|-------|-----------------|
| | | |

Examples: NORSOK P-001, ISO 6976, DNV-OS-F101, API 520, ASME B31.3, Operator TR1414

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
- [ ] Literature papers: [place PDFs in `references/` folder, summarise in `notes.md`]
- [ ] Other: [specify]

## Reference Fluid Compositions

Pick a starting composition or define your own. All values in mol%.

### Typical Lean Pipeline Gas

| Component | mol% |
|-----------|------|
| methane | 85.0 |
| ethane | 7.0 |
| propane | 3.0 |
| i-butane | 0.5 |
| n-butane | 0.5 |
| i-pentane | 0.1 |
| n-pentane | 0.1 |
| nitrogen | 1.5 |
| CO2 | 2.3 |

### Typical Rich Gas (with condensate potential)

| Component | mol% |
|-----------|------|
| methane | 75.0 |
| ethane | 10.0 |
| propane | 5.0 |
| i-butane | 1.5 |
| n-butane | 2.0 |
| i-pentane | 0.5 |
| n-pentane | 0.5 |
| n-hexane | 0.3 |
| nitrogen | 1.0 |
| CO2 | 4.2 |

### Typical Wet Gas (with water for hydrate/dehydration studies)

| Component | mol% |
|-----------|------|
| methane | 80.0 |
| ethane | 6.0 |
| propane | 3.0 |
| n-butane | 1.0 |
| CO2 | 2.0 |
| nitrogen | 1.0 |
| water | 7.0 |

### Typical CO2-Rich Stream (CCS)

| Component | mol% |
|-----------|------|
| CO2 | 95.0 |
| nitrogen | 2.0 |
| methane | 1.5 |
| water | 1.0 |
| H2S | 0.5 |

> **Note:** Adapt these to your specific project data. For oil systems with
> C7+ fractions, use the `@thermo.fluid` agent or define TBP/plus fractions.
"""

STUDY_CONFIG = "\n".join([
    "# Study configuration for NeqSim task solver.",
    "# Values here override scale inferred from the prompt when an agent plans the task.",
    "",
    "study:",
    "  title: \"[Title]\"",
    "  task_type: \"B\"",
    "  scale: auto          # auto | quick | standard | comprehensive",
    "  mode: auto           # auto | screening | design | development",
    "  aace_class: auto     # auto | 5 | 4 | 3 | 2 | 1",
    "  fel_stage: auto      # auto | FEL-1 | FEL-2 | FEL-3",
    "  deliverable_mode: auto  # auto | answer-first | notebook-first | report-first",
    "",
    "intake:",
    "  pause_after_folder_creation: auto  # auto | always | never",
    "  ask_for_missing_info: true",
    "  allow_user_file_drop: true",
    "  confirm_before_notebooks: true",
    "",
    "inputs:",
    "  prompt_file: \"\"       # Optional text/markdown file used as the original task prompt.",
    "  document_root: \"\"     # Source document library, root + all subfolders. Empty = not configured.",
    "  documents_required: false",
    "  document_extraction_required: auto  # auto | required | optional | skip",
    "  documents:",
    "    - path: step1_scope_and_research/references/",
    "      role: reference_documents",
    "      required: false",
    "      extraction: classify_extract_normalize_validate",
    "  data_sources_required: false  # true when the answer comes from source systems, not documents",
    "  data_sources: []",
    "  # Declare every source system the study reads so the report gate can check that",
    "  # captured evidence exists for each one.",
    "  #  - system: sap_maintenance   # historian | stid | sap_maintenance | pdm | synergi | lab | vendor",
    "  #    scope: \"plant 1820, notifications 2015-2026\"",
    "  #    access: read_only",
    "  #    evidence: step2_analysis/sap_pull.json",
    "",
    "analysis:",
    "  engine: auto           # auto | notebook | script | hybrid",
    "  resumable_jobs: false  # long source-system pulls must restart without re-reading everything",
    "  scripts: []",
    "  # Script-backed studies (API pulls, long batch jobs, non-NeqSim data analysis)",
    "  # declare their scripts here instead of notebooks, and set notebooks.required: false.",
    "  #  - file: 01_pull_source_data.py",
    "  #    purpose: Read the source systems and cache the raw records.",
    "  #    produces: step2_analysis/raw_records.json",
    "",
    "notebooks:",
    "  required: true                   # false for script-backed studies (see analysis.engine)",
    "  execution_required: true",
    "  execution_engine: neqsim_runner  # neqsim_runner | interactive | auto",
    "  runner_mode: execute             # execute | script",
    "  runner_max_retries: 3",
    "  runner_timeout_seconds: 3600",
    "  runner_max_parallel: 1           # JVM-heavy jobs should stay serial by default",
    "  runner_merge_results: true       # Merge multi-notebook results.json updates",
    "  require_successful_jobs: true    # Report gate warns on failed/timed-out jobs",
    "  isolated_subprocess: true",
    "  minimum_count: 1",
    "  plan:",
    "    - file: 01_main_analysis.ipynb",
    "      purpose: Main NeqSim model, result extraction, figures, and results.json.",
    "    - file: 02_benchmark_validation.ipynb",
    "      purpose: Independent benchmark checks when required by scale or scope.",
    "    - file: 03_uncertainty_and_risk.ipynb",
    "      purpose: Monte Carlo, tornado sensitivity, and risk register when applicable.",
    "",
    "report:",
    "  formats:",
    "    - docx",
    "    - html",
    "    # - pdf",
    "  depth: auto          # auto | brief | standard | detailed",
    "  include_paper: false",
    "  work_record: auto    # auto | required | skip -> step3_report/WORK_RECORD.md",
    "  # The work record is the method-and-provenance companion to the report: what was",
    "  # done, how, which data and scripts were used, and where every file lives.",
    "  # Build it with: neqsim work-record <task folder>",
    "  required_sections:",
    "    - executive_summary",
    "    - scope_and_standards",
    "    - information_sources   # how many documents from which source system",
    "    - methodology",
    "    - results",
    "    - discussion",
    "    - validation",
    "    - assumptions_and_gaps  # every assumption, and what was assumed where data was missing",
    "    - conclusions",
    "    - references",
    "",
    "quality_gates:",
    "  require_results_json: true",
    "  benchmark_validation: auto     # auto | required | optional | skip",
    "  benchmark_kind: auto           # auto | reference_data | peer_fleet | plant_data | none",
    "  provenance_closure: auto       # every declared data source must have captured evidence",
    "  work_record: auto              # auto | required | skip (method and data record)",
    "  human_review: auto             # auto | required | optional (decision-grade output)",
    "  uncertainty_analysis: auto     # auto | required | optional | skip",
    "  risk_register: auto            # auto | required | optional | skip",
    "  figure_discussion: required    # required | optional | skip",
    "  consistency_checker: required  # required | optional | skip",
    "  minimum_figures: 2",
    "  notebook_execution: required   # required | optional | skip",
    "",
])

STEP2_NOTES = """# Step 2: Analysis & Validation Notes

## Analysis Log

### Run 1 - YYYY-MM-DD

**Setup:**

**Results:**

**Validation against acceptance criteria:**

**Status:** Pass / Needs refinement

---

## Validation Summary

Fill this table as you validate each check. This maps directly to the
`validation` section in `results.json` and auto-populates the report.

| Check | Status | Value / Note |
|-------|--------|--------------|
| Mass balance (in = out +/- tolerance) | | |
| Energy balance | | |
| Temperatures in reasonable range | | |
| Pressures positive | | |
| Densities in expected range | | |
| Results consistent with literature | | |
| Acceptance criteria from task_spec met | | |
| Sensitivity to key parameters checked | | |
| All deliverables from task_spec produced | | |

## Sensitivity Analysis (if applicable)

Document any parameter sweeps performed:

| Parameter Varied | Range | Effect on Output | Conclusion |
|------------------|-------|------------------|------------|
| | | | |

## Comparison with Reference Data (if applicable)

| Source | Parameter | Reference Value | NeqSim Value | Deviation |
|--------|-----------|----------------|--------------|----------|
| | | | | |

## results.json Status

- [ ] results.json saved from notebook (see task README for pattern)
- [ ] key_results section populated with all key outputs
- [ ] validation section populated with all checks above
- [ ] approach and conclusions fields filled in
- [ ] figure_captions populated with custom captions for key plots
- [ ] figure_discussion populated for each decision-critical figure
      (observation, mechanism, implication, recommendation, linked_results)
- [ ] equations populated with key equations used in the analysis
- [ ] benchmark_validation populated if comparing against reference data
- [ ] uncertainty populated if sensitivity/Monte Carlo was performed
- [ ] risk_evaluation populated if risk assessment was performed
- [ ] Figures saved to figures/ using absolute TASK_DIR path (not os.getcwd!)
"""

GENERATE_REPORT = '''#!/usr/bin/env python3
"""Run the canonical NeqSim report generator against this task folder.

Task folders used to vendor a full copy of the generator, which then forked as
the generator improved — two tasks with identical results.json could render
different reports. This launcher keeps the familiar entry point while the code
stays in one place: <neqsim>/devtools/task_template/step3_report/.

    python step3_report/generate_report.py [--paper] [--template PATH]
    neqsim report .                              # identical

The generator is located via NEQSIM_PROJECT_ROOT, the path recorded when this
task was created, the saved project root in ~/.neqsim/task_defaults.json, then
a walk up from this file looking for a NeqSim checkout.
"""
import json
import os
import runpy
import sys

TASK_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GENERATOR_HINT = r"__GENERATOR_HINT__"
RELATIVE = os.path.join("devtools", "task_template", "step3_report",
                        "generate_report.py")


def _saved_project_root():
    """Return the project root recorded by the task tooling, if any."""
    path = os.path.join(os.path.expanduser("~"), ".neqsim", "task_defaults.json")
    try:
        with open(path, "r", encoding="utf-8-sig") as handle:
            return json.load(handle).get("project_root") or ""
    except (OSError, ValueError, AttributeError):
        return ""


def _candidates():
    """Yield possible locations of the canonical generator, best first."""
    for root in (os.environ.get("NEQSIM_PROJECT_ROOT"), _saved_project_root()):
        if root:
            yield os.path.join(root, RELATIVE)
    if GENERATOR_HINT:
        yield GENERATOR_HINT
    here = os.path.abspath(__file__)
    for _ in range(8):
        parent = os.path.dirname(here)
        if parent == here:
            break
        here = parent
        if os.path.isfile(os.path.join(here, "pom.xml")):
            yield os.path.join(here, RELATIVE)


def _find_generator():
    """Return the first canonical generator that exists on disk."""
    for candidate in _candidates():
        if candidate and os.path.isfile(candidate):
            return os.path.abspath(candidate)
    return None


def main():
    """Execute the canonical generator with this task folder as the target."""
    generator = _find_generator()
    if not generator:
        print("ERROR: canonical report generator not found.")
        print("Set NEQSIM_PROJECT_ROOT to your NeqSim checkout, or run:")
        print('  neqsim report "{}"'.format(TASK_DIR))
        return 2
    argv = list(sys.argv[1:])
    if "--task-dir" not in argv:
        argv = ["--task-dir", TASK_DIR] + argv
    sys.argv = [generator] + argv
    runpy.run_path(generator, run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
'''


# ══════════════════════════════════════════════════════════
# Functions
# ══════════════════════════════════════════════════════════

def slugify(title, max_length=60):
    """Convert a title to a folder-safe slug.

    The slug is capped at ``max_length`` characters (cut at a word boundary
    where possible) so the resulting task folder path stays well under the
    Windows 260-character limit once nested step folders are added.

    @param title the human-readable task title
    @param max_length the maximum slug length in characters
    @return a lowercase underscore-separated slug, truncated if needed
    """
    slug = title.lower().strip()
    for ch in ",:;!?()[]{}'\"/\\.":
        slug = slug.replace(ch, "")
    slug = slug.replace(" ", "_").replace("-", "_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    slug = slug.strip("_")
    if len(slug) > max_length:
        cut = slug[:max_length]
        # Prefer to break at the last whole word to keep the slug readable.
        last_us = cut.rfind("_")
        if last_us >= max_length // 2:
            cut = cut[:last_us]
        slug = cut.strip("_")
    return slug


def _write_file(path, content):
    """Write content to a file, creating parent dirs as needed."""
    parent = os.path.dirname(path)
    if parent and not os.path.exists(parent):
        os.makedirs(parent)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def setup_workspace(task_root=None):
    """
    Create the task_solve/ folder with README and TASK_TEMPLATE.

    Safe to call multiple times — skips files that already exist.
    Returns True if anything was created.
    """
    task_root = resolve_task_root(task_root)
    template_dir = os.path.join(task_root, "TASK_TEMPLATE")
    created = False

    # Main README
    readme = os.path.join(task_root, "README.md")
    if not os.path.exists(readme):
        _write_file(readme, WORKSPACE_README)
        created = True

    # Template structure
    template_files = {
        os.path.join(TEMPLATE_DIR, "README.md"): TASK_README,
        os.path.join(TEMPLATE_DIR, "study_config.yaml"): STUDY_CONFIG,
        os.path.join(TEMPLATE_DIR, "step1_scope_and_research", "task_spec.md"): TASK_SPEC,
        os.path.join(TEMPLATE_DIR, "step1_scope_and_research", "notes.md"): STEP1_NOTES,
        os.path.join(TEMPLATE_DIR, "step1_scope_and_research", "references", "README.md"): REFERENCES_README,
        os.path.join(TEMPLATE_DIR, "step2_analysis", "notes.md"): STEP2_NOTES,
        os.path.join(TEMPLATE_DIR, "step2_analysis", ".gitkeep"): "",
        os.path.join(TEMPLATE_DIR, "step3_report", "generate_report.py"): GENERATE_REPORT,
        os.path.join(TEMPLATE_DIR, "figures", ".gitkeep"): "",
    }

    for path, content in template_files.items():
        path = os.path.join(template_dir, os.path.relpath(path, TEMPLATE_DIR))
        if not os.path.exists(path):
            _write_file(path, content)
            created = True

    # Overlay tracked canonical templates from devtools/task_template/ if present.
    # These are the authoritative versions of files like starter notebooks, so
    # improvements made to devtools/task_template/ automatically propagate to
    # new tasks without updating the embedded strings in this script.
    # generate_report.py is deliberately NOT copied: tasks get a launcher shim
    # so the 4800-line generator has exactly one copy that everyone runs.
    canonical_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                 "task_template")
    if os.path.isdir(canonical_dir):
        for root, dirs, files in os.walk(canonical_dir):
            dirs[:] = [dirname for dirname in dirs if dirname != "__pycache__"]
            for fname in files:
                if fname.endswith((".pyc", ".pyo")):
                    continue
                src = os.path.join(root, fname)
                rel = os.path.relpath(src, canonical_dir)
                if rel.replace("\\", "/") == VENDORED_GENERATOR_REL:
                    continue
                dst = os.path.join(template_dir, rel)
                # Always overwrite with canonical version
                _write_file(dst, open(src, "r", encoding="utf-8").read())
                created = True

    # Always refresh the launcher so a template created before this change
    # stops carrying a full copy of the generator.
    _write_file(os.path.join(template_dir, "step3_report", "generate_report.py"),
                GENERATE_REPORT)

    return created


def normalize_scale(scale):
    """Normalize task scale names used by agents and CLI users."""
    if not scale:
        return ""
    value = scale.strip().lower()
    aliases = {
        "auto": "auto",
        "quick": "quick",
        "screening": "quick",
        "standard": "standard",
        "design": "standard",
        "comprehensive": "comprehensive",
        "development": "comprehensive",
    }
    return aliases.get(value, "")


def normalize_intake_pause(intake_pause):
    """Normalize intake pause settings used by agents and CLI users."""
    if not intake_pause:
        return ""
    value = intake_pause.strip().lower()
    aliases = {
        "auto": "auto",
        "always": "always",
        "yes": "always",
        "true": "always",
        "on": "always",
        "never": "never",
        "no": "never",
        "false": "never",
        "off": "never",
    }
    return aliases.get(value, "")


def _yaml_quote(value):
    """Return a simple double-quoted YAML scalar."""
    text = str(value).replace("\\", "\\\\").replace('"', '\\"')
    text = text.replace("\r", " ").replace("\n", " ")
    return '"{}"'.format(text)


def _replace_section_key(content, section, key, value):
    """Replace an indented YAML key inside a top-level section."""
    lines = content.splitlines()
    in_section = False
    section_marker = "{}:".format(section)
    key_marker = "{}:".format(key)
    for line_index, line in enumerate(lines):
        stripped = line.strip()
        if stripped == section_marker and not line.startswith(" "):
            in_section = True
            continue
        if in_section and stripped and not line.startswith(" "):
            in_section = False
        if in_section and stripped.startswith(key_marker):
            indent = line[:len(line) - len(line.lstrip())]
            lines[line_index] = "{}{}: {}".format(indent, key, value)
            return "\n".join(lines) + "\n"
    return content


def _scale_defaults(scale):
    """Return default mode, AACE class, FEL stage, and report depth for a scale."""
    defaults = {
        "quick": ("screening", "5", "FEL-1", "brief"),
        "standard": ("design", "3", "FEL-2", "standard"),
        "comprehensive": ("development", "2", "FEL-3", "detailed"),
    }
    return defaults.get(scale, ("auto", "auto", "auto", "auto"))


def _notebook_files_from_option(notebooks):
    """Parse --notebooks as either a count or comma/semicolon-separated files."""
    if not notebooks:
        return []
    text = notebooks.strip()
    if text.isdigit():
        count = max(1, int(text))
        return ["{:02d}_analysis.ipynb".format(number)
                for number in range(1, count + 1)]
    filenames = []
    for item in text.replace(";", ",").split(","):
        filename = item.strip()
        if not filename:
            continue
        if not filename.lower().endswith(".ipynb"):
            filename += ".ipynb"
        filenames.append(filename)
    return filenames


def _default_notebook_plan(scale):
    """Return a default notebook plan for explicit task scales."""
    if scale == "quick":
        return ["01_main_analysis.ipynb"]
    if scale == "standard":
        return ["01_main_analysis.ipynb", "02_benchmark_validation.ipynb"]
    if scale == "comprehensive":
        return [
            "01_scope_basis_and_fluid.ipynb",
            "02_process_simulation.ipynb",
            "03_benchmark_validation.ipynb",
            "04_uncertainty_and_risk.ipynb",
            "05_report_tables_and_figures.ipynb",
        ]
    return []


def _replace_notebook_plan(content, notebook_files):
    """Replace the notebooks.plan list in the YAML config."""
    if not notebook_files:
        return content
    lines = content.splitlines()
    updated = []
    line_index = 0
    while line_index < len(lines):
        line = lines[line_index]
        updated.append(line)
        if line.strip() == "plan:":
            plan_indent = len(line) - len(line.lstrip())
            line_index += 1
            while line_index < len(lines):
                next_line = lines[line_index]
                next_indent = len(next_line) - len(next_line.lstrip())
                if next_line.strip() and next_indent <= plan_indent:
                    break
                line_index += 1
            item_indent = " " * (plan_indent + 2)
            field_indent = " " * (plan_indent + 4)
            for number, filename in enumerate(notebook_files, 1):
                updated.append("{}- file: {}".format(item_indent, _yaml_quote(filename)))
                updated.append("{}purpose: Notebook {} from configured task plan.".format(
                    field_indent, number))
            continue
        line_index += 1
    return "\n".join(updated) + "\n"


def _apply_study_config_overrides(content, title, task_type, scale,
                                  report_depth, notebooks, intake_pause):
    """Apply CLI/default values to study_config.yaml content."""
    content = _replace_section_key(content, "study", "title", _yaml_quote(title))
    content = _replace_section_key(content, "study", "task_type", _yaml_quote(task_type))

    try:
        document_root = resolve_document_root()
    except (OSError, ValueError) as error:
        print("  WARNING: document root is not usable: {}".format(error))
        document_root = None
    content = _replace_section_key(content, "inputs", "document_root",
                                   _yaml_quote(document_root or ""))

    normalized_scale = normalize_scale(scale)
    if normalized_scale:
        mode, aace_class, fel_stage, default_depth = _scale_defaults(normalized_scale)
        content = _replace_section_key(content, "study", "scale", normalized_scale)
        content = _replace_section_key(content, "study", "mode", mode)
        content = _replace_section_key(content, "study", "aace_class", aace_class)
        content = _replace_section_key(content, "study", "fel_stage", fel_stage)
        if not report_depth:
            report_depth = default_depth
        if normalized_scale == "comprehensive":
            content = _replace_section_key(content, "quality_gates",
                                           "benchmark_validation", "required")
            content = _replace_section_key(content, "quality_gates",
                                           "uncertainty_analysis", "required")
            content = _replace_section_key(content, "quality_gates",
                                           "risk_register", "required")

    if report_depth:
        content = _replace_section_key(content, "report", "depth", report_depth.strip().lower())

    normalized_intake_pause = normalize_intake_pause(intake_pause)
    if normalized_intake_pause:
        content = _replace_section_key(content, "intake",
                                       "pause_after_folder_creation",
                                       normalized_intake_pause)

    notebook_files = _notebook_files_from_option(notebooks)
    if not notebook_files and normalized_scale:
        notebook_files = _default_notebook_plan(normalized_scale)
    if notebook_files:
        content = _replace_section_key(content, "notebooks", "minimum_count",
                                       str(len(notebook_files)))
        content = _replace_notebook_plan(content, notebook_files)
    return content


def _seed_study_config(task_dir, title, task_type, scale, report_depth,
                       notebooks, config_file, intake_pause):
    """Create or update study_config.yaml for a new task."""
    config_path = os.path.join(task_dir, "study_config.yaml")
    if config_file:
        source_path = os.path.abspath(config_file)
        try:
            with open(source_path, "r", encoding="utf-8") as source:
                content = source.read()
        except Exception as error:
            print("  WARNING: could not read --config-file: {}".format(error))
            content = STUDY_CONFIG
    elif os.path.exists(config_path):
        with open(config_path, "r", encoding="utf-8") as existing:
            content = existing.read()
    else:
        content = STUDY_CONFIG

    content = _apply_study_config_overrides(content, title, task_type, scale,
                                            report_depth, notebooks, intake_pause)
    with open(config_path, "w", encoding="utf-8") as config:
        config.write(content)


def create_task(title, task_type="B", author="", prompt="", scale="",
                report_depth="", notebooks="", config_file="",
                intake_pause="", task_root=None):
    """Create a new task folder from the template.

    Parameters
    ----------
    title : str
        Short task title (used for slug and headings).
    task_type : str
        One of A/B/C/D/E/F/G — see TASK_TYPES.
    author : str
        Optional author name written into README and report.
    prompt : str
        Optional verbatim user request. Written into user_input.md so the
        task can be reproduced from the original chat input.
    scale : str
        Optional task scale override: quick, standard, or comprehensive.
    report_depth : str
        Optional report detail override: brief, standard, or detailed.
    notebooks : str
        Optional notebook plan as a count or comma-separated notebook files.
    config_file : str
        Optional path to a study_config.yaml file to copy into the task.
    intake_pause : str
        Optional intake pause setting: auto, always, or never.
    task_root : str
        Optional parent folder overriding the environment and saved default.
    """
    task_root = resolve_task_root(task_root)
    template_dir = os.path.join(task_root, "TASK_TEMPLATE")
    # Ensure workspace exists
    if not os.path.exists(template_dir):
        print("Setting up task workspace: {}".format(task_root))
        setup_workspace(task_root)
        print("")
    else:
        # Always refresh the canonical overlay so updates to
        # devtools/task_template/ propagate to new tasks without requiring
        # a full --setup re-run.
        setup_workspace(task_root)

    today = date.today().isoformat()
    folder_name = "{}_{}".format(today, slugify(title))
    task_dir = os.path.join(task_root, folder_name)

    if os.path.exists(task_dir):
        print("ERROR: Folder already exists: {}".format(task_dir))
        sys.exit(1)

    # Copy template
    shutil.copytree(
        template_dir,
        task_dir,
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.pyo"),
    )

    # Seed explicit task-depth configuration before the agent starts planning.
    _seed_study_config(task_dir, title, task_type, scale, report_depth,
                       notebooks, config_file, intake_pause)

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
    content = content.replace("task_solve/[THIS_FOLDER]", task_dir.replace("\\", "/"))
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

    # Point the launcher at the canonical generator in this checkout, and
    # record the same root so later tooling can find it from anywhere.
    report_path = os.path.join(task_dir, "step3_report", "generate_report.py")
    with open(report_path, "r", encoding="utf-8") as f:
        report = f.read()
    report = report.replace("__GENERATOR_HINT__", canonical_generator_path())
    report = report.replace('TITLE = "Task Report"', 'TITLE = "{}"'.format(title))
    if author:
        report = report.replace('AUTHOR = ""', 'AUTHOR = "{}"'.format(author))
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report)
    save_project_root()

    # Seed user_input.md with the original user request so the task can be
    # reproduced from the same chat input. The agent should append clarifying
    # Q&A and follow-up instructions to this file as the conversation evolves.
    user_input_path = os.path.join(task_dir, "user_input.md")
    if os.path.exists(user_input_path):
        try:
            with open(user_input_path, "r", encoding="utf-8") as f:
                ui = f.read()
            from datetime import datetime
            stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
            ui = ui.replace("**Date/Time:** YYYY-MM-DD HH:MM",
                            "**Date/Time:** {}".format(stamp))
            if prompt:
                marker = "<!-- ORIGINAL_USER_PROMPT -->"
                placeholder_block = (
                    "<!-- ORIGINAL_USER_PROMPT -->\n"
                    "[Paste the user's original prompt here, verbatim. "
                    "Do not paraphrase, summarise,\nor \"clean up\" wording. "
                    "Include code blocks, file paths, numbers, units, and\n"
                    "typos as given.]"
                )
                if placeholder_block in ui:
                    ui = ui.replace(placeholder_block,
                                    marker + "\n" + prompt.strip())
            with open(user_input_path, "w", encoding="utf-8") as f:
                f.write(ui)
        except Exception as e:
            print("  WARNING: could not seed user_input.md ({})".format(e))

    print("Created: {}".format(task_dir))
    print("")
    print("Task input can be added before analysis starts:")
    print("  Config: {}/study_config.yaml".format(task_dir))
    print("  Prompt/log: {}/user_input.md".format(task_dir))
    print("  Document input: {}/step1_scope_and_research/references/".format(task_dir))
    print("  Supported documents include PDFs, Word files, Excel stream tables,")
    print("  P&IDs, vendor data sheets, standards, and lab reports.")
    intake_setting = normalize_intake_pause(intake_pause) or "auto"
    if intake_setting == "always":
        print("  Intake pause: requested - wait for user confirmation before notebooks.")
    print("")
    print("Next steps (pick one):")
    print("")
    print("  Recommended - Let Copilot do everything:")
    print("    Open VS Code Copilot Chat and type:")
    print("")
    print("    @solve.task {}".format(title))
    print("")
    print("  Alternative - Follow prompts manually:")
    print("    Open {}/README.md".format(task_dir))
    print("")
    return task_dir


def list_tasks(task_root=None):
    """List existing task folders."""
    task_root = resolve_task_root(task_root)
    if not os.path.exists(task_root):
        print("No task folder at {}. Run: neqsim new-task --setup".format(task_root))
        return

    entries = sorted(os.listdir(task_root))
    tasks = [
        e for e in entries
        if os.path.isdir(os.path.join(task_root, e))
        and e != "TASK_TEMPLATE"
    ]

    if not tasks:
        print("No tasks yet. Create one with: neqsim new-task \"your task\"")
    else:
        print("Tasks in {}:".format(task_root))
        for t in tasks:
            readme = os.path.join(task_root, t, "README.md")
            status = ""
            if os.path.exists(readme):
                with open(readme, "r", encoding="utf-8") as f:
                    for line in f:
                        if "**Status:**" in line:
                            status = line.split("**Status:**")[-1].strip()
                            break
            print("  {} {}".format(t, "[{}]".format(status) if status else ""))


def main():
    """Handle destination settings before forwarding task creation arguments."""
    parser = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
    parser.add_argument("--task-root")
    settings = parser.add_mutually_exclusive_group()
    settings.add_argument("--set-default-folder")
    settings.add_argument("--reset-default-folder", action="store_true")
    settings.add_argument("--show-task-root", action="store_true")
    settings.add_argument("--set-report-template")
    settings.add_argument("--reset-report-template", action="store_true")
    settings.add_argument("--show-report-template", action="store_true")
    settings.add_argument("--set-document-root")
    settings.add_argument("--reset-document-root", action="store_true")
    settings.add_argument("--show-document-root", action="store_true")
    options, remaining = parser.parse_known_args()
    try:
        if options.set_default_folder is not None:
            stored = save_default_task_root(options.set_default_folder)
            if stored == CWD_TASK_ROOT:
                print("Saved default task folder: the terminal's current folder.")
            else:
                print("Saved default task folder: {}".format(stored))
            return
        if options.reset_default_folder:
            clear_default_task_root()
            print("Saved default removed. Existing tasks are unchanged.")
            return
        if options.show_task_root:
            print(resolve_task_root(options.task_root))
            return
        if options.set_report_template is not None:
            print("Saved report template: {}".format(
                save_default_report_template(options.set_report_template)))
            return
        if options.reset_report_template:
            clear_default_report_template()
            print("Saved report template removed. Reports use built-in styling.")
            return
        if options.show_report_template:
            print(resolve_report_template() or "(none — reports use built-in styling)")
            return
        if options.set_document_root is not None:
            print("Saved document root: {}".format(
                save_default_document_root(options.set_document_root)))
            return
        if options.reset_document_root:
            clear_default_document_root()
            print("Saved document root removed.")
            return
        if options.show_document_root:
            print(resolve_document_root() or "(none — no document root configured)")
            return
        _main([sys.argv[0]] + remaining, options.task_root)
    except (OSError, ValueError, TypeError, AttributeError) as error:
        parser.error(str(error))


def _main(argv, task_root=None):
    """Run task commands using the selected output root."""
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)

    if argv[1] == "--setup":
        if setup_workspace(task_root):
            print("Created task workspace at {}".format(resolve_task_root(task_root)))
        else:
            print("Task workspace already exists at {}".format(resolve_task_root(task_root)))
        print("\nCreate a task: neqsim new-task \"your task title\"")
        return

    if argv[1] == "--list":
        list_tasks(task_root)
        return

    title = argv[1]
    task_type = "B"
    author = ""
    prompt = ""
    scale = ""
    report_depth = ""
    notebooks = ""
    config_file = ""
    intake_pause = ""

    i = 2
    while i < len(argv):
        if argv[i] == "--type" and i + 1 < len(argv):
            task_type = argv[i + 1].upper()
            i += 2
        elif argv[i] == "--author" and i + 1 < len(argv):
            author = argv[i + 1]
            i += 2
        elif argv[i] == "--prompt" and i + 1 < len(argv):
            prompt = argv[i + 1]
            i += 2
        elif argv[i] == "--prompt-file" and i + 1 < len(argv):
            try:
                with open(argv[i + 1], "r", encoding="utf-8") as f:
                    prompt = f.read()
            except Exception as e:
                print("WARNING: could not read --prompt-file: {}".format(e))
            i += 2
        elif argv[i] == "--scale" and i + 1 < len(argv):
            scale = argv[i + 1]
            i += 2
        elif argv[i] == "--report-depth" and i + 1 < len(argv):
            report_depth = argv[i + 1]
            i += 2
        elif argv[i] == "--notebooks" and i + 1 < len(argv):
            notebooks = argv[i + 1]
            i += 2
        elif argv[i] == "--intake-pause" and i + 1 < len(argv):
            intake_pause = argv[i + 1]
            i += 2
        elif argv[i] == "--config-file" and i + 1 < len(argv):
            config_file = argv[i + 1]
            i += 2
        else:
            i += 1

    if task_type not in TASK_TYPES:
        print("WARNING: Unknown task type '{}'. Valid: {}".format(
            task_type, ", ".join(sorted(TASK_TYPES.keys()))))
        print("Using type B (Process) as default.")
        task_type = "B"

    if scale and not normalize_scale(scale):
        print("WARNING: Unknown scale '{}'. Valid: quick, standard, comprehensive.".format(scale))
        print("Using scale auto-detection in study_config.yaml.")
        scale = ""

    if intake_pause and not normalize_intake_pause(intake_pause):
        print("WARNING: Unknown intake pause '{}'. Valid: auto, always, never.".format(
            intake_pause))
        print("Using intake pause auto-detection in study_config.yaml.")
        intake_pause = ""

    create_task(title, task_type, author, prompt, scale, report_depth,
                notebooks, config_file, intake_pause, task_root=task_root)


if __name__ == "__main__":
    main()
