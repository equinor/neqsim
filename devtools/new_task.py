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
}


# ══════════════════════════════════════════════════════════
# Embedded templates — these are the "source of truth" so
# new users get them even though task_solve/ is gitignored.
# ══════════════════════════════════════════════════════════

WORKSPACE_README = r"""# AI-Supported Task Solving While Developing

This folder is a **local working area** for solving engineering tasks using the
4-step AI-assisted workflow. It is in `.gitignore` — nothing here is committed.
Each task gets its own subfolder with research notes, simulation results,
figures, and reports.

> **New to NeqSim?** Run `python devtools/new_task.py "your task"` to get started.

---

## Prerequisites

| Requirement | Install / Setup | Used In |
|-------------|----------------|---------|
| Python 3.8+ | [python.org](https://python.org) | All steps |
| Java JDK 8+ | Bundled via `devtools/` setup | Step 2 simulations |
| NeqSim dev tools | `pip install -e devtools/` (from repo root) | Step 2 — boots the JVM and gives you `neqsim_dev_setup` |
| VS Code + GitHub Copilot Chat | VS Code marketplace | Steps 2-4 AI assistance |
| python-docx | `pip install python-docx matplotlib` | Step 4 Word report |
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

1. Run: `python devtools/new_task.py "hydrate temperature for wet gas" --type A`
2. Open VS Code Copilot Chat
3. Paste the prompt from the generated README
4. The AI creates a notebook, runs it, and gives you results

### Path B: Developer (I want to extend NeqSim)

You're solving a task AND improving the NeqSim codebase. When the API is
missing something, you add it mid-task — new methods, equipment, or models.

1. Run: `python devtools/new_task.py "add JT coefficient method" --type E`
2. Work through all 4 steps
3. Promote reusable code back into `src/main/`, `src/test/`, or `examples/`
4. Log the solution in `docs/development/TASK_LOG.md`

### Path C: Researcher (I need a technical assessment)

You're producing a deliverable — a report, technology screening, or design
study. The 4 steps map directly to a professional workflow.

1. Run: `python devtools/new_task.py "CCS pipeline design assessment" --type F --author "Your Name"`
2. Step 1: Literature review (NotebookLM or Copilot)
3. Step 2: NeqSim simulations with Copilot
4. Step 3: Iterate with Claude Opus 4.6 to refine
5. Step 4: Generate Word report

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

---

## The 4-Step Workflow

```
 STEP 1              STEP 2              STEP 3              STEP 4
 Research            Technical Analysis  Iterative Result    Final Technical
                     via NeqSim &        Evaluation          Writing
 Google NotebookLM   Copilot                                 -> Word Report
   or Copilot                            GitHub Copilot
                     NeqSim API +        + Claude Opus 4.6   Claude Opus 4.6
 + open sources      GitHub Copilot                          + GitHub Copilot
 Build knowledge     Deep analysis       Refine results
 base                                                        Synthesize into
                                                             assessment
```

### Step 1: Research (Google NotebookLM or GitHub Copilot)

Use **either** tool — or both — depending on your workflow:

#### Option A: Google NotebookLM (best for deep literature review)
- Upload PDFs, standards documents, and technical papers
- Ask questions across all your sources at once
- Get cited answers with references back to source pages
- Good for: collecting correlations, comparing standards, summarising long documents

#### Option B: GitHub Copilot in VS Code (best for code-adjacent research)
- Open Copilot Chat and ask research questions directly
- Copilot can search the web, read repo docs, and summarise findings
- Create a markdown file in `step1_research/` and ask Copilot to populate it
- Good for: quick lookups, NeqSim API questions, formula verification

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
   Write the findings to step1_research/notes.md in my task folder.
   ```
3. Review and refine — ask follow-up questions
4. Save the final notes in `step1_research/`

**General guidance for Step 1:**
- Build a comprehensive knowledge base on the topic
- Collect relevant papers, standards, correlations, and reference data
- Save research notes and sources in `step1_research/`

### Step 2: Technical Analysis via NeqSim & Copilot
- Combine GitHub Copilot with the NeqSim physics-based API
- Use operational data for deep technology analysis
- Write simulations (Java tests or Jupyter notebooks) inside the repo
- Save simulation code, results, and data in `step2_analysis/`

### Step 3: Iterative Result Evaluation
- Use GitHub Copilot and Claude Opus 4.6 to evaluate results
- Refine the analysis through continuous iterations
- Compare against literature, experimental data, and known benchmarks
- Validate physics: mass balance, energy balance, reasonable ranges
- Document iterations and refinements in `step3_evaluation/`

### Step 4: Final Technical Writing -> Word Report
The deliverable for every completed task is a **Word report** (`.docx`).
- Synthesize all findings into a professional technology assessment
- Generate Word report using `python-docx` via `generate_report.py`
- Include all figures from `figures/` directory
- Run: `python step4_report/generate_report.py`

---

## VS Code Agent Quick Reference

| Agent | Best For | Example |
|-------|----------|---------|
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

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `python` not found | Use `python3` or install Python from python.org |
| Copilot Chat doesn't know NeqSim | Start prompt with "Read CONTEXT.md for orientation" |
| Simulation gives wrong numbers | Check EOS choice, mixing rule, units (Kelvin vs Celsius!) |
| Want to share task with colleague | Zip the task folder and send it - it's self-contained |
"""

TASK_README = r"""# Task: [Title]

**Date:** YYYY-MM-DD
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design)
**Status:** In Progress | Complete

## Problem Statement

[Describe the engineering question or task]

---

## Step 1: Research

- [ ] Literature search completed
- [ ] Reference data collected
- [ ] Key sources documented in `step1_research/notes.md`

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
Search the web and this repository for:
1. Key physical principles and governing equations
2. Typical operating ranges and design rules of thumb
3. Relevant industry standards (API, ASME, ISO, NORSOK, DNV)
4. What NeqSim classes/methods already exist for this
Write the findings to step1_research/notes.md in my task folder.
```

---

## Step 2: Technical Analysis

- [ ] NeqSim simulation written (notebook or test)
- [ ] Results extracted and saved
- [ ] API gaps identified (if any)

**AI prompt - paste into VS Code Copilot Chat:**

```
I'm working on a task in task_solve/[THIS_FOLDER]/.
Read task_solve/README.md for the 4-step workflow.

Task: [DESCRIBE YOUR TASK]

Create a Jupyter notebook in step2_analysis/ that:
1. Sets up the fluid system with appropriate EOS
2. Builds the process flowsheet
3. Runs the simulation
4. Extracts key results and saves figures to figures/
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

## Step 3: Iterative Evaluation

- [ ] Results validated against references
- [ ] Physics checks passed (mass/energy balance, ranges)
- [ ] Iterations documented in `step3_evaluation/notes.md`

**AI prompt - paste into VS Code Copilot Chat:**

```
Review the simulation results in step2_analysis/.
Check:
- Are temperatures, pressures, and densities in physically reasonable ranges?
- Does mass balance close (in = out)?
- Does energy balance close?
- How do results compare against the reference data in step1_research/notes.md?
- What sensitivity analysis should we run?
Suggest refinements and iterate.
```

---

## Step 4: Final Report (Word Document)

The deliverable is a **Word report** (`.docx`). Use the `generate_report.py`
starter in `step4_report/` to create it.

- [ ] Figures saved to `figures/`
- [ ] `generate_report.py` created and runs end-to-end
- [ ] Word report generated: `step4_report/Report.docx`
- [ ] Task logged in `docs/development/TASK_LOG.md`

**To generate the report:**

```powershell
pip install python-docx matplotlib    # one-time setup
python step4_report/generate_report.py
```

**AI prompt - paste into VS Code Copilot Chat:**

```
Create a generate_report.py in step4_report/ that produces a Word document.
Use python-docx. The report must include:
1. Title page with task name, date, and author
2. Executive summary (3-5 sentences)
3. Problem description and approach
4. Key results with figures embedded from figures/
5. Table of key numerical results
6. Conclusions and recommendations
7. References from step1_research/notes.md
Use matplotlib.use('Agg') for headless figure generation.
The script must run end-to-end without Jupyter: python step4_report/generate_report.py
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

STEP3_NOTES = """# Step 3: Evaluation Notes

## Iteration Log

### Iteration 1 - YYYY-MM-DD

**What was tested:**

**Results:**

**Comparison against reference:**

**Decision:** Accept / Refine / Reject

---

## Validation Checklist

- [ ] Mass balance closes (in = out +/- 0.1%)
- [ ] Energy balance closes
- [ ] Temperatures in reasonable range
- [ ] Pressures positive
- [ ] Densities in expected range
- [ ] Results consistent with literature/correlations
- [ ] Sensitivity to key parameters checked
"""

GENERATE_REPORT = '''"""
generate_report.py - Generate a Word report for this task.

Usage:
    pip install python-docx matplotlib   (one-time setup)
    python step4_report/generate_report.py

This script runs headless (no Jupyter kernel needed). It:
1. Collects results from step2_analysis/
2. Embeds figures from figures/
3. Produces a .docx Word report in step4_report/

Customize the sections below for your specific task.
"""
import os
import sys
import glob
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
OUTPUT_FILE = os.path.join(REPORT_DIR, "Report.docx")

# Configuration (edit these)
TITLE = "Task Report"           # <-- Change to your task title
AUTHOR = ""                     # <-- Your name
TASK_DATE = date.today().isoformat()


def build_report():
    """Build the Word document."""
    doc = Document()

    # Title page
    doc.add_heading(TITLE, level=0)
    doc.add_paragraph("")
    doc.add_paragraph("Author: {}".format(AUTHOR or "(not specified)"))
    doc.add_paragraph("Date: {}".format(TASK_DATE))
    doc.add_page_break()

    # 1. Executive Summary
    doc.add_heading("1. Executive Summary", level=1)
    doc.add_paragraph(
        "[Replace this with a 3-5 sentence summary of the task, approach, "
        "and key findings.]"
    )

    # 2. Problem Description
    doc.add_heading("2. Problem Description", level=1)
    doc.add_paragraph(
        "[Describe the engineering question or task that was solved.]"
    )

    # 3. Approach
    doc.add_heading("3. Approach", level=1)
    doc.add_paragraph(
        "[Describe the methodology: EOS used, process configuration, "
        "simulation setup, key assumptions.]"
    )

    # 4. Results
    doc.add_heading("4. Results", level=1)
    doc.add_paragraph(
        "[Present key numerical results. Add tables and figures below.]"
    )

    # Embed all figures from figures/ directory
    figures = sorted(glob.glob(os.path.join(FIG_DIR, "*.png")))
    if figures:
        for fig_path in figures:
            fig_name = os.path.basename(fig_path)
            doc.add_picture(fig_path, width=Inches(6.0))
            last_para = doc.paragraphs[-1]
            last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
            caption = doc.add_paragraph(
                "Figure: {}".format(fig_name.replace("_", " ").replace(".png", ""))
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

    # 5. Conclusions
    doc.add_heading("5. Conclusions and Recommendations", level=1)
    doc.add_paragraph(
        "[Summarize key findings and provide recommendations.]"
    )

    # 6. References
    doc.add_heading("6. References", level=1)
    doc.add_paragraph(
        "[List references from step1_research/notes.md.]"
    )

    # Save
    doc.save(OUTPUT_FILE)
    print("Report saved: {}".format(OUTPUT_FILE))


if __name__ == "__main__":
    build_report()
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
        os.path.join(TEMPLATE_DIR, "step1_research", "notes.md"): STEP1_NOTES,
        os.path.join(TEMPLATE_DIR, "step2_analysis", ".gitkeep"): "",
        os.path.join(TEMPLATE_DIR, "step3_evaluation", "notes.md"): STEP3_NOTES,
        os.path.join(TEMPLATE_DIR, "step4_report", "generate_report.py"): GENERATE_REPORT,
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
        "A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design)",
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
    notes_path = os.path.join(task_dir, "step1_research", "notes.md")
    with open(notes_path, "r", encoding="utf-8") as f:
        notes = f.read()
    notes = notes.replace(
        "[Summary of the engineering context]",
        "Task: {}".format(title),
    )
    with open(notes_path, "w", encoding="utf-8") as f:
        f.write(notes)

    # Fill in generate_report.py
    report_path = os.path.join(task_dir, "step4_report", "generate_report.py")
    with open(report_path, "r", encoding="utf-8") as f:
        report = f.read()
    report = report.replace('TITLE = "Task Report"', 'TITLE = "{}"'.format(title))
    if author:
        report = report.replace('AUTHOR = ""', 'AUTHOR = "{}"'.format(author))
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report)

    print("Created: task_solve/{}".format(folder_name))
    print("")
    print("Next steps:")
    print("  1. Edit task_solve/{}/README.md".format(folder_name))
    print("  2. Or paste this into VS Code Copilot Chat:")
    print("")
    print("     I'm working on a task in task_solve/{}/".format(folder_name))
    print("     Task: {}".format(title))
    print("     Follow the 4-step workflow in task_solve/README.md.")
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
