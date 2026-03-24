---
title: "How to Solve an Engineering Task with NeqSim"
description: "Complete hands-on guide to solving engineering tasks using the AI-assisted 3-step workflow. Covers setup, scope definition, simulation, validation, report generation, and contributing results back. Works with VS Code Copilot, OpenAI Codex, Claude Code, or manually."
---

# How to Solve an Engineering Task with NeqSim

This guide walks you through solving a real engineering task — from a blank
screen to a validated Word/HTML report — using NeqSim's AI-assisted workflow.

**What you'll learn:**

- How to set up the task-solving environment
- How to define scope and research background
- How to build and validate a simulation
- How to generate professional reports
- How to contribute your results back to the project

**Time:** 15–60 minutes depending on task complexity.

---

## The Core Idea

LLMs are excellent at understanding engineering intent — they know what a
hydrate curve is, which standard applies, and how to structure a report. But
they hallucinate thermodynamics. NeqSim is the opposite: exact on physics,
but blind to context.

The workflow combines them:

| **LLM (Claude / Copilot)** | **NeqSim (Physics Engine)** |
|---|---|
| Understands the study intent | Calculates rigorous thermodynamic truth |
| Orchestrates workflows and tool calls | Computes complex phase behavior and K-values |
| Interprets risks in engineering language | Enforces mass and energy balances |
| Gracefully handles incomplete information | Manages edge conditions without hallucination |

The LLM writes the code. NeqSim runs the physics. You get validated results.

---

## Prerequisites

Before you start, make sure you have:

| Requirement | Install Command | Purpose |
|-------------|----------------|---------|
| Python 3.8+ | [python.org](https://python.org) | Runs notebooks and reports |
| Java JDK 8+ | [adoptium.net](https://adoptium.net) | NeqSim physics engine |
| Git | [git-scm.com](https://git-scm.com) | Clone the repo |
| VS Code + Copilot Chat | VS Code marketplace | AI assistance (optional) |

**One-time setup:**

```powershell
# Clone the repo
git clone https://github.com/equinor/neqsim.git
cd neqsim

# Build NeqSim (compiles the Java physics engine)
./mvnw install          # Linux/Mac
mvnw.cmd install        # Windows

# Install Python helpers
pip install -e devtools/              # NeqSim dev setup (boots JVM from your local build)
pip install python-docx matplotlib    # For reports and plots
```

> **Important:** Use `pip install -e devtools/` — this ensures your notebooks
> run against your local Java build, not the released package. When you add a
> new method and rebuild, it's immediately available.

---

## Quick Start: Let the AI Do Everything

If you have VS Code with GitHub Copilot Chat, this is the fastest path:

```
@solve.task hydrate formation temperature for wet gas at 100 bara
```

The `@solve.task` agent handles the entire workflow:
1. Creates the task folder
2. Fills in the task specification (standards, methods, acceptance criteria)
3. Researches the topic
4. Builds and runs a Jupyter notebook with NeqSim simulation
5. Validates results
6. Generates Word + HTML reports

You can guide the depth by what you ask for:

| What You Type | Scale |
|---------------|-------|
| "density of CO2 at 200 bar" | Quick — one calculation, brief answer |
| "TEG dehydration for 50 MMSCFD per NORSOK P-001" | Standard — full analysis with report |
| "field development flow assurance per NORSOK and DNV" | Comprehensive — multi-notebook study |

**Don't have Copilot?** Continue to the manual workflow below — it works with
any AI tool or no AI at all.

---

## Manual Workflow: Step by Step

### Step 0: Create the Task Folder

Run the task creation script:

```powershell
python devtools/new_task.py "hydrate formation temperature for wet gas" --type A --author "Your Name"
```

This creates a folder like:

```
task_solve/
└── 2026-03-07_hydrate_formation_temperature_for_wet_gas/
    ├── README.md                          # Task checklist with AI prompts
    ├── results.json                       # (created later by your notebook)
    ├── figures/                           # Saved plots
    ├── step1_scope_and_research/
    │   ├── task_spec.md                   # Scope document — standards, methods, criteria
    │   ├── notes.md                       # Research notes, literature review
    │   └── references/                    # PDFs, standards, lab reports
    ├── step2_analysis/
    │   └── notes.md                       # Validation log
    └── step3_report/
        └── generate_report.py             # Produces Word + HTML reports
```

**Task types:**

| Type | Use When | Example |
|------|----------|---------|
| **A** — Property | Single property calculation | Density, viscosity, JT coefficient |
| **B** — Process | Process simulation | TEG dehydration, compression train |
| **C** — PVT | Lab experiment simulation | CME, CVD, swelling test |
| **D** — Standards | Gas/oil quality per standard | ISO 6976 Wobbe index |
| **E** — Feature | Adding new code to NeqSim | New equipment model, bug fix |
| **F** — Design | Mechanical/structural design | Pipeline wall thickness, vessel sizing |
| **G** — Workflow | Multi-discipline study | Field development, design basis |

> **Tip:** The script auto-creates `task_solve/` on first run. Run
> `python devtools/new_task.py --list` to see existing tasks.

---

### Step 1: Scope and Research

This step defines **what** you're solving and **how**. The scope document
(`task_spec.md`) governs everything in Step 2.

#### Part A: Fill in the Task Specification

Open `step1_scope_and_research/task_spec.md` and fill in each section:

**Applicable Standards** — Which codes govern the work?

```markdown
## Applicable Standards

| Standard | Scope | Key Requirements |
|----------|-------|-----------------|
| NORSOK P-001 | Process design | Hydrate temperature margin |
| ISO 6976 | Gas properties | Heating value calculation |
| DNV-OS-F101 | Pipeline design | Wall thickness formula |
```

**Calculation Methods** — Which models and equations to use?

```markdown
## Calculation Methods & Models

- **Equation of State:** SRK with classic mixing rule
- **Hydrate model:** CPA with van der Waals-Platteeuw
- **Flow model:** Beggs & Brill for multiphase pipe flow
```

**Acceptance Criteria** — What does "good enough" mean?

```markdown
## Acceptance Criteria

- **Mass balance:** < 0.1% error
- **Hydrate temperature accuracy:** within 1°C of experimental data
- **Convergence:** All flash calculations converge (no VU-flash failures)
```

**Operating Envelope** — Range of conditions to cover:

```markdown
## Operating Envelope

| Parameter | Min | Design | Max | Unit |
|-----------|-----|--------|-----|------|
| Pressure | 50 | 100 | 200 | bara |
| Temperature | -10 | 15 | 40 | °C |
| Water content | 0 | 500 | 2000 | ppm |
```

> **Why this matters:** The task specification turns a vague request into a
> well-defined engineering problem. It gives the AI (and you) clear targets
> to validate against. Without it, you don't know when you're done.

#### Part B: Research

Gather background knowledge. You have two options:

**Option 1: Google NotebookLM** (best for deep literature review)

1. Place PDFs, standards, and papers in `step1_scope_and_research/references/`
2. Upload them to [NotebookLM](https://notebooklm.google.com)
3. Ask questions across all sources at once
4. Paste findings into `notes.md`

**Option 2: GitHub Copilot Chat** (best for code-adjacent research)

Paste this prompt into Copilot Chat:

```
I'm researching hydrate formation for a NeqSim task.
Search the web and this repository for:
1. Key physical principles and governing equations
2. Typical operating ranges and design rules of thumb
3. Relevant industry standards (NORSOK, DNV, ISO)
4. What NeqSim classes/methods already exist for this
Write the findings to step1_scope_and_research/notes.md
```

**Providing literature and reference documents:**

If you have technical papers, standards PDFs, or lab reports:

1. Save them in `step1_scope_and_research/references/`
2. Use descriptive names: `Andreasen_2021_HydDown_JOSS.pdf`, `DNV-OS-F101_2021.pdf`
3. Summarise key contributions in `notes.md` under "Literature & Reference Documents"

---

### Step 2: Analysis and Evaluation

This is where you build, run, and validate the simulation. Analysis and
evaluation happen in one iterative loop — you don't need separate steps.

#### Create a Jupyter Notebook

Create a notebook in `step2_analysis/`. Every notebook starts with this
setup cell:

```python
# Dual-boot: works with local dev build OR pip-installed neqsim
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

#### Build the Simulation

Import NeqSim classes and build your simulation:

```python
from neqsim import jneqsim

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 15.0, 100.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.07)
fluid.addComponent("propane", 0.03)
fluid.addComponent("water", 0.05)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

# Run flash
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.init(3)

# Read results
print(f"Density: {fluid.getDensity('kg/m3'):.1f} kg/m3")
print(f"Phases: {fluid.getNumberOfPhases()}")
```

For process simulations:

```python
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator

# Build flowsheet
process = ProcessSystem()
feed = Stream("feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")
process.add(feed)

sep = Separator("HP Sep", feed)
process.add(sep)

process.run()

# Results
gas_rate = sep.getGasOutStream().getFlowRate("kg/hr")
print(f"Gas rate: {gas_rate:.0f} kg/hr")
```

#### Validate Results

Check your results against the acceptance criteria from `task_spec.md`:

```python
# Validation checks
mass_in = feed.getFlowRate("kg/hr")
mass_out = (sep.getGasOutStream().getFlowRate("kg/hr")
            + sep.getLiquidOutStream().getFlowRate("kg/hr"))
mass_balance_error = abs(mass_in - mass_out) / mass_in * 100

print(f"Mass balance error: {mass_balance_error:.4f}%")
assert mass_balance_error < 0.1, "Mass balance exceeds 0.1% tolerance"
```

#### Save Figures

Save every plot as a PNG — the report generator picks them up automatically:

```python
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import os, pathlib

NOTEBOOK_DIR = pathlib.Path(globals().get(
    "__vsc_ipynb_file__", os.path.abspath("step2_analysis/notebook.ipynb")
)).resolve().parent
TASK_DIR = NOTEBOOK_DIR.parent
FIG_DIR = TASK_DIR / "figures"
FIG_DIR.mkdir(exist_ok=True)

fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(pressures, temperatures, 'b-', linewidth=2)
ax.set_xlabel("Pressure (bara)")
ax.set_ylabel("Temperature (°C)")
ax.set_title("Hydrate Formation Temperature")
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig(str(FIG_DIR / "hydrate_curve.png"), dpi=150, bbox_inches='tight')
plt.close(fig)
```

#### Save Results to results.json

At the end of your notebook, save structured results. The report generator
auto-reads this file:

```python
import json

results = {
    "key_results": {
        "hydrate_temperature_C": 18.5,
        "pressure_drop_bar": 3.2,
        "gas_flow_rate_kg_hr": 42500,
    },
    "validation": {
        "mass_balance_error_pct": 0.01,
        "acceptance_criteria_met": True,
    },
    "approach": "Used SRK EOS with classic mixing rule. Hydrate temperature "
                "calculated using CPA with van der Waals-Platteeuw model.",
    "conclusions": "Hydrate formation temperature is 18.5°C at 100 bara. "
                   "MEG injection of 30 wt% provides 10°C subcooling margin.",
    "figure_captions": {
        "hydrate_curve.png": "Hydrate formation temperature vs pressure for wet gas"
    },
    "tables": [
        {
            "title": "Sensitivity to MEG Concentration",
            "headers": ["MEG wt%", "Hydrate T (°C)", "Subcooling (°C)"],
            "rows": [
                [0, 18.5, 0],
                [20, 12.3, 6.2],
                [30, 8.1, 10.4],
            ]
        }
    ],
    "references": [
        {"id": "NORSOK-P001", "text": "NORSOK P-001 Rev.6 (2020). Process Systems."},
        {"id": "Sloan2008", "text": "Sloan, E.D. & Koh, C. (2008). Clathrate Hydrates of Natural Gases. 3rd Ed."}
    ]
}

with open(str(TASK_DIR / "results.json"), "w") as f:
    json.dump(results, f, indent=2)
print("Saved results.json")
```

---

### Step 3: Generate Reports

Run the report generator:

```powershell
cd task_solve/2026-03-07_hydrate_formation_temperature_for_wet_gas
python step3_report/generate_report.py
```

This produces two files:
- **Report.docx** — Word document for formal distribution
- **Report.html** — Interactive HTML with navigation sidebar

Both reports auto-populate from your data:

| Source | What It Fills In |
|--------|-----------------|
| `task_spec.md` | Section 3: Scope and Standards (tables, lists, methods) |
| `results.json` → `key_results` | Section 4: Results (formatted table with units) |
| `results.json` → `validation` | Section 5: Validation (PASS/FAIL with color coding) |
| `results.json` → `approach` | Section 2: Approach and Methodology |
| `results.json` → `conclusions` | Section 7: Conclusions |
| `results.json` → `tables` | Custom tables rendered in both formats |
| `results.json` → `references` | Section 8: References (numbered list) |
| `figures/*.png` | Section 6: Figures (embedded with captions) |
| `results.json` → `equations` | Equations rendered with KaTeX (HTML) or images (Word) |

> **No copy-paste needed.** The report reflects your latest simulation results
> automatically. Rerun the notebook, rerun the report generator, and the
> report updates.

---

## After the Task: Contributing Back

Every solved task can benefit the next person. Choose your level:

### Minimum: Log It

Add an entry to `docs/development/TASK_LOG.md`:

```markdown
### 2026-03-07 — Hydrate formation temperature for wet gas
**Type:** A (Property)
**Keywords:** hydrate, formation, temperature, wet gas, CPA, MEG, inhibitor
**Solution:** task_solve/2026-03-07_hydrate_formation_temperature/step2_analysis/
**Notes:** Used SRK EOS. Hydrate T = 18.5°C at 100 bara. 30 wt% MEG gives
10°C subcooling margin. CPA model needed for accurate water-gas interaction.
```

### Medium: Share Your Notebook

Copy your notebook to `examples/notebooks/`:

```powershell
Copy-Item task_solve/.../step2_analysis/hydrate_analysis.ipynb examples/notebooks/
```

### Full: Create a Pull Request

If you extended NeqSim with new methods, tests, or documentation:

```powershell
git checkout -b task/hydrate-formation
# Copy reusable outputs to proper locations
Copy-Item task_solve/.../HydrateTest.java src/test/java/neqsim/thermo/
Copy-Item task_solve/.../notebook.ipynb examples/notebooks/
git add src/test/ examples/notebooks/ docs/development/TASK_LOG.md
git commit -m "Add hydrate formation temperature analysis"
git push -u origin task/hydrate-formation

---

## Further Reading

- [Introduction to Agentic Engineering](../integration/ai_agentic_programming_intro) — What agentic programming is and how NeqSim uses it
- [Agents and Skills Reference](../integration/ai_agents_reference) — Complete catalog of all 16 agents and 14 skills
- [Agentic Workflow Examples](../integration/ai_workflow_examples) — Step-by-step walkthroughs from simple to complex
- [Code Patterns](../development/CODE_PATTERNS) — Copy-paste code starters for common tasks
- [Task Log](../development/TASK_LOG) — Search past solved tasks before starting
gh pr create --title "Add hydrate formation analysis" --body "From task-solving workflow"
```

> **Safety:** Never commit `task_solve/` contents directly — copy the
> reusable files to their proper locations first.

---

## Using Other AI Tools

The workflow is not tied to VS Code Copilot. The script, templates, and report
generator work from any terminal.

### OpenAI Codex

Codex reads `AGENTS.md` (repo root) automatically. The `.openapi/codex.yaml`
sets up the sandbox with Java 8, Maven, Python, and `gh` CLI.

Give it a one-shot prompt:

```
Read AGENTS.md for project instructions.
Create a task: python devtools/new_task.py "hydrate temperature" --type A
Follow the 3-step workflow in the generated README.
Save results.json and generate the Word + HTML report.
Create a PR with the outputs.
```

### Claude Code / Cursor

Point the agent to the workflow documentation:

```
Read docs/development/TASK_SOLVING_GUIDE.md for the full workflow.
Read the task README at task_solve/YYYY-MM-DD_your_task/README.md.
Follow the 3-step workflow.
```

### No AI — Pure Manual

The workflow works without any AI. You fill in `task_spec.md` yourself, write
the notebook manually using NeqSim's Java API via Python, and run the report
generator.

---

## Common Patterns

### Fluid Creation

```python
from neqsim import jneqsim

# Natural gas (temperature in Kelvin, pressure in bara)
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")       # NEVER skip this
```

### Process Simulation

```python
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor

process = ProcessSystem()

feed = Stream("feed", fluid)
feed.setFlowRate(100000.0, "kg/hr")
process.add(feed)

sep = Separator("HP Sep", feed)
process.add(sep)

comp = Compressor("Comp", sep.getGasOutStream())
comp.setOutletPressure(120.0, "bara")
process.add(comp)

process.run()
```

### Flash Calculation

```python
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()         # Temperature-Pressure flash
fluid.init(3)         # Initialize all properties

density = fluid.getDensity("kg/m3")
enthalpy = fluid.getEnthalpy("kJ/kg")
```

### Reading Results

```python
# Stream properties
stream.getTemperature()              # Kelvin
stream.getTemperature() - 273.15     # Convert to °C
stream.getPressure()                 # bara
stream.getFlowRate("kg/hr")          # Mass flow with unit string

# Fluid properties
fluid = stream.getFluid()
fluid.getDensity("kg/m3")
fluid.getViscosity("cP")
fluid.getMolarMass("kg/mol")
fluid.getZ()                         # Compressibility factor
fluid.getNumberOfPhases()
fluid.hasPhaseType("gas")

# Equipment results
compressor.getPower("kW")
heater.getDuty()                     # Watts
```

---

## EOS Selection Guide

| System | Recommended EOS | Why |
|--------|----------------|-----|
| Dry natural gas | `SystemSrkEos` | Standard, well-validated |
| Gas with water/MEG/TEG | `SystemSrkCPAstatoil` | CPA handles polar molecules |
| Oil with heavy fractions | `SystemPrEos` | Better for liquid densities |
| CO2-rich (CCS) | `SystemSrkCPAstatoil` | CPA for CO2-water interaction |
| Hydrogen systems | `SystemSrkEos` | With appropriate mixing rules |
| High accuracy needed | `SystemPrEos` | With volume translation |

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| `ModuleNotFoundError: neqsim` | Not installed | Run `pip install -e devtools/` |
| JVM won't start | Java not found | Install JDK 8+ and set `JAVA_HOME` |
| Flash doesn't converge | Wrong EOS or bad composition | Check mole fractions sum to 1.0, try different EOS |
| Report generator fails | Missing python-docx | Run `pip install python-docx matplotlib` |
| Word file permission error | File open in Word | Close the .docx file and rerun |
| Numbers look wrong | Temperature in Kelvin | NeqSim uses Kelvin internally — subtract 273.15 for °C |
| `RuntimeError: JVM cannot be restarted` | JVM already stopped | Restart the Jupyter kernel |

---

## Where to Find More

| Resource | Path | What It Covers |
|----------|------|----------------|
| 60-second orientation | [CONTEXT.md](../../CONTEXT.md) | Repo map, build commands, constraints |
| Full workflow reference | [TASK_SOLVING_GUIDE.md](../development/TASK_SOLVING_GUIDE) | Detailed workflow, AI agents, extending the API |
| Code patterns | [CODE_PATTERNS.md](../development/CODE_PATTERNS) | Copy-paste starters for every task type |
| Past solved tasks | [TASK_LOG.md](../development/TASK_LOG) | Search before starting — it may be solved already |
| Python quickstart | [Python Quickstart](../quickstart/python-quickstart) | Minimal Python setup |
| TEG tutorial | [TEG Dehydration](teg_dehydration_tutorial) | Complete process simulation walkthrough |
| GOSP tutorial | [Gas-Oil Separation Plant](gosp_tutorial) | Multi-stage separation example |
| Reference manual | [REFERENCE_MANUAL_INDEX.md](../REFERENCE_MANUAL_INDEX) | 360+ documentation pages indexed |
