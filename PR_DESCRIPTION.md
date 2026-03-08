# Add AI-Supported Task-Solving Workflow with Field Development Extensions

## Summary

Introduces a structured 3-step workflow called **"AI-Supported Task Solving While Developing"** that lets users solve advanced engineering tasks **while simultaneously improving and extending the NeqSim toolbox itself**. This PR also demonstrates the workflow in action — solving a field development NPV task revealed missing capabilities, which were then added as new Java classes and tests.

The workflow **adapts to any scale** — from a quick property lookup (one notebook cell, done in minutes) to a comprehensive Class A field development study (multiple discipline-specific notebooks, full standards compliance, navigable HTML report). The agent decides the depth based on your request: simple question → quick answer; multi-standard engineering study → comprehensive deliverable set.

The key insight: every real engineering task — hydrate prediction for a new field, sizing a CO2 compression train, designing a subsea pipeline — pushes NeqSim into new territory. When the existing API doesn't cover the task, the workflow guides users to **add the missing capability** (a new method, a new equipment model, a better correlation) right there in the same session. The task gets solved, and NeqSim gets better for the next person.

This turns task-solving from a one-off activity into a **development flywheel**: tasks drive new features, new features enable harder tasks, and everything is logged and reusable.

## What's New

### `@solve.task` — The Default Entry Point

Open VS Code Copilot Chat and type:

```
@solve.task JT cooling for rich gas at 100 bara
```

The agent handles the full 3-step workflow end-to-end:
1. Creates the `task_solve/` folder via `new_task.py`
2. Fills in the **task specification** (standards, methods, deliverables, acceptance criteria)
3. Researches the topic (searches codebase, web, past tasks)
4. Builds and runs a Jupyter notebook with NeqSim simulation
5. Validates results against acceptance criteria (iteration is implicit)
6. Generates Word + HTML reports with actual findings
7. Flags API gaps — and those gaps become development tasks

The agent delegates to specialist agents (`@thermo.fluid`, `@solve.process`, etc.) for complex sub-tasks. This is how advanced tasks get solved **while** the toolbox improves.

> **Manual alternative:** `python devtools/new_task.py "your task"` creates the folder with templates for a step-by-step approach.

### `devtools/new_task.py` — Self-bootstrapping task creation script

A single Python script (tracked in git, zero external dependencies) that:

- **Auto-creates** the `task_solve/` workspace on first run (no manual setup needed)
- **Generates** a new task folder from a built-in template with one command
- **Embeds all templates** as string constants — the script is the single source of truth

```powershell
python devtools/new_task.py "JT cooling for rich gas" --type A
python devtools/new_task.py "TEG dehydration sizing" --type B --author "Your Name"
python devtools/new_task.py "field development concept" --type G  # multi-discipline workflow
python devtools/new_task.py --setup    # just create workspace, no task
python devtools/new_task.py --list     # list existing tasks
```

### The 3-Step Workflow

```
 STEP 1                    STEP 2                    STEP 3
 Scope & Research          Analysis & Evaluation     Report

 Define standards,         Build simulation,         Word + HTML
 methods, deliverables     run, validate, iterate    deliverables

 + task_spec.md            NeqSim API +              python-docx
 + literature review       GitHub Copilot            + HTML template
```

Each task folder gets:
- **step1_scope_and_research/** — `task_spec.md` (standards, methods, deliverables, acceptance criteria) + `notes.md` (literature review)
- **step2_analysis/** — simulation code (notebooks / Java tests) + validation notes
- **step3_report/** — `generate_report.py` that produces both Word (.docx) and HTML reports
- **figures/** — saved plots for embedding in reports

### Task Specification — Guiding the Analysis

The `task_spec.md` file in Step 1 is the **scope document** that governs everything in Step 2. It defines:

| Section | Purpose | Example |
|---------|---------|---------|
| **Applicable Standards** | Which codes/standards govern the work | NORSOK P-001, DNV-OS-F101, ISO 6976, Equinor TR1414 |
| **Calculation Methods** | Which EOS, correlations, models to use | SRK-CPA for polar systems, Beggs & Brill for pipe flow |
| **Required Deliverables** | What the output must include | Phase envelopes, sizing calcs, sensitivity plots, VFP tables |
| **Acceptance Criteria** | What "good enough" means | Mass balance < 0.1%, design factor 0.72 per DNV |
| **Operating Envelope** | Range of conditions to cover | 50-150 bara, 5-80°C, 10k-100k kg/hr |

This means users (or the AI agent) can specify upfront: "use NORSOK P-001, calculate per ISO 6976, deliver a phase envelope + sizing report, and accept if mass balance < 0.1%".

### Merged Analysis & Evaluation

The old Steps 2 (Analysis) and 3 (Evaluation) are merged into a single Step 2 (Analysis & Evaluation). Iteration is implicit — you build, run, validate, and refine in one natural loop without needing a separate "evaluation step". Validation notes live alongside the simulation code in `step2_analysis/notes.md`.

### Adaptive Complexity — One Workflow, Any Scale

The same `@solve.task` command handles everything from quick lookups to Class A studies. The agent auto-detects the task scale based on user request:

| Scale | When Used | Depth |
|-------|-----------|-------|
| **Quick** | Simple question, single property, one condition | Minimal task_spec, few cells, brief summary |
| **Standard** | Process simulation, single-discipline design, PVT study | Full task_spec, complete notebook, Word + HTML reports |
| **Comprehensive** | Multiple standards, multi-discipline, "Class A", "design basis" | Detailed task_spec with all standards, multiple numbered notebooks, full HTML with navigation |

Users control depth through their request — mentioning standards, deliverables, and acceptance criteria naturally increases analysis depth. Compare:
- "density of CO2 at 200 bar" → Quick
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
