---
title: NeqSim Agent Reference
description: "Full reference for AI coding agents working on NeqSim: task-solving workflow, results.json schema, Automation API and closed-loop optimization patterns, process and mechanical-design code patterns, key paths, skills table, Java 8, JavaDoc HTML5, logging, Spotless, Jekyll front matter, KaTeX and documentation-link rules."
---

The always-loaded agent files (`AGENTS.md`, `.github/copilot-instructions.md`) keep only
the rules every agent needs on every turn. This page holds the detail they point to.
**Do not read it end to end** - search for the heading or term you need and read that
section only. Domain detail lives in the skills under `.github/skills/`.

## Part A - Workflow, API patterns and key paths (formerly the full AGENTS.md)

### Quick Orientation

Read `CONTEXT.md` for a 60-second overview of the codebase (repo map, build
commands, code patterns).

### Critical Constraint: Docker Required for Linux-Only Tools on Windows

Some skills wrap external engines that have **no native Windows build** and are
Linux software: **FluidMagic** (fluid characterization engine,
`enterprise-fluidmagic-characterization`), **OpenFOAM** (CFD,
`neqsim-cfd-coupling`), **OPM Flow** (reservoir simulation,
`neqsim-near-well-and-injectivity`), and any other skill/agent that shells out
to a Linux-only binary. On a Windows machine, install **Docker Desktop** (or
WSL2) before attempting to run one of these — the request/case/hand-off can
still be built and written without it, but nothing executes locally until a
Linux runtime is available. Report the missing Docker/WSL2 environment as a
blocker instead of silently skipping the calculation.

### Critical Constraint: Java 8

**All code MUST compile with Java 8.** Never use:
- `var`, `List.of()`, `Map.of()`, `Set.of()`, `String.repeat()`,
  `str.isBlank()`, `str.strip()`, text blocks (`"""`), records, pattern
  matching `instanceof`, `Optional.isEmpty()`.
- Use explicit types, `Arrays.asList()`, `StringUtils.repeat()`, `str.trim().isEmpty()`.

### Critical Constraint: Logging

**All Java logging/output MUST use Log4j2 logger.** Never use `System.out.println` or
`System.err.println` in production code, tests, examples, or generated snippets.

Use class logger fields and parameterized logging calls:
- `private static final Logger logger = LogManager.getLogger(YourClass.class);`
- `logger.info("Message {}", value);`
- `logger.warn("Message {}", value);`
- `logger.error("Message {}", ex.getMessage(), ex);`

### Critical Constraint: Code Formatting (Spotless)

**AI-generated Java is NOT auto-formatted.** After creating or editing ANY `.java`
file you MUST run Spotless before committing — do not rely on local pre-commit
hooks being installed:

```bash
./mvnw spotless:apply    # reformats all Java to the project style
./mvnw spotless:check    # verifies formatting (this is what CI runs)
```

- Formatter profile: Eclipse `.config/neqsim_formatter.xml` (configured in `pom.xml`),
  applied to `src/main/java` and `src/test/java`.
- CI runs `./mvnw spotless:check` and **fails the build on any unformatted file**.
- NEVER bypass the gate with `git commit --no-verify`.
- Run `spotless:apply`, then `git add` the reformatted files, then commit.

### MCP-First Calculation Policy (when an MCP server is available)

For any single NeqSim calculation (flash, PVT, process, standards, sizing, flow
assurance, ...), check first whether a curated `mcp_neqsim_*` tool covers it
(`runFlash`, `runProcess`, `runPVT`, `getPhaseEnvelope`, `sizeEquipment`,
`calculateStandard`, `runFlowAssurance`, `runBatch`, ...) and use it directly —
confirm field names with `getSchema`/`validateInput` first. If no curated tool
matches, try the generic `runCapability` route (`search` -> `inspectApi` ->
invoke) before writing code. Only fall back to the Python API (`import
neqsim`) or Java in a checkout when MCP genuinely cannot do the job
(`runCapability` reports `inspect-only`, or the task needs loops, plotting,
state, notebooks, or reports), or when the task lives inside `/solve-task`
(task folders, validators, report generation are Python-only). If NeqSim
itself lacks the capability, implement it in Java with tests rather than
working around the gap in Python. See the `neqsim-api-patterns` skill §
"MCP server vs. Python/Java API" for the full decision matrix.

### Continuous Improvement of Agents & Skills (always-on default)

Improving the agents and skills you use — and their **cooperation** — is part of
every task, not an optional extra. Default behaviour, no need to ask: when a task
reveals that a `SKILL.md` or `*.agent.md` is missing a pattern, naming
convention, API signature, gotcha, or a needed agent↔skill hand-off, fix it in
the same session. Improve cooperation too: add cross-references in both
directions between related skills, keep hand-off shapes consistent along the
chain, and update agent "Loaded skills" lists and router/composition guidance
when a useful multi-skill/multi-agent pipeline is found. Keep site-specific
detail in the enterprise repos and community content plant-agnostic; follow each
repo's front-matter/validation conventions; note the improvements in the task
summary; do not over-engineer.

### Build & Test

```bash
./mvnw install                            # full build (Linux/Mac)
mvnw.cmd install                          # Windows
./mvnw test -Dtest=SeparatorTest          # single test class
./mvnw test -Dtest=SeparatorTest#testTwo  # single method
./mvnw spotless:apply                     # auto-format Java (run before committing)
./mvnw spotless:check                     # verify formatting (CI gate)
./mvnw checkstyle:check spotbugs:check pmd:check  # static analysis
```

### Python Environment Reuse

Use the Python interpreter explicitly selected by the user or parent workflow. If
none was selected, use `C:\appl\neqsim-venv\Scripts\python.exe`. In command examples,
`<python-executable>` means that selected absolute path. Subagents and child
processes must reuse it through the same absolute path or `sys.executable`; they
must not open interpreter selection, invoke bare `python`/`py`/`pip`/`pytest`,
create or activate separate environments, reinstall or reconfigure the shared
environment, or silently fall back to another interpreter. If the executable or a
required package is unavailable, report the blocker before changing runtimes.

### Solving Engineering Tasks (Primary Workflow)

#### Configurable Task Destination (Overrides Literal Paths Below)

For new tasks, resolve the parent folder with `neqsim --show-task-root`.
Precedence: explicit `--task-root PATH`, `NEQSIM_TASK_ROOT`, saved user default
in `~/.neqsim/task_defaults.json`, then this repository's `task_solve/`.
Save a default once with `neqsim --set-task-root "PATH"` (or `cwd` to follow the
terminal's folder); remove it with `neqsim --reset-task-root` (no task files moved).
The same controls exist as `neqsim new-task --set-default-folder/--show-task-root/--reset-default-folder`.
All literal `task_solve/` paths below are examples relative to that resolved root.
Use the absolute task path returned by creation for all artifacts and validators,
and pass that same path to every child agent, runner, and external tool explicitly.
Resume existing tasks in place; a changed default applies only to new tasks.
Keep `NEQSIM_PROJECT_ROOT` pointing at the NeqSim source repository for external
tasks; `NEQSIM_TASK_DIR` identifies one active task, not the parent destination.
Never silently fall back if the configured destination cannot be read or written.

#### Configurable Document Root (Source Documents for All Tasks)

The document root is **optional** — it is either set or undefined. When set, that
folder **and all its subfolders** are the source library for every task:
`neqsim --set-document-root "PATH"`, `neqsim --show-document-root`,
`neqsim --reset-document-root`, and `neqsim documents [PATTERN]` to search it
recursively (the dash-less spelling and an unquoted path with spaces both work).
Precedence: explicit path > `NEQSIM_DOCUMENT_ROOT` > the saved `document_root` in
`~/.neqsim/task_defaults.json` > none. Each new task records the resolved value as
`inputs.document_root` in its `study_config.yaml` (empty when undefined), and
lists the library's files in
`step1_scope_and_research/references/document_root_index.md`, so a resumed task
and every child agent see what is available without running the CLI (refresh it
with `neqsim documents --index <task_dir>`). Search it before reporting
a standard, datasheet, drawing or vendor document as unavailable. It is read-only —
never write task output there; copy the documents a task uses into that task's
`step1_scope_and_research/references/<source>/`. When undefined, work from
user-supplied documents and log the missing evidence as a data gap; a configured
folder that is missing or unreadable is a reported blocker, not a silent fallback.

**Plant documents come from the retrieval backend, not the document root.** When
`devtools/doc_retrieval_config.yaml` configures a backend (e.g. STID via
`stidapi`), `neqsim new-task` and every Standard-first living cycle run
`devtools/doc_retriever.py` automatically; run `neqsim fetch-docs <task_dir>`
yourself when resuming a task or when a data gap appears. It infers the
installation from the task text and downloads ranked P&IDs and data sheets into
`references/stid/`. Never declare P&IDs, data sheets or drawings unavailable
until `references/stid/retrieval_status.json` shows a concrete blocker
(`no_backend`, `no_installation`, `no_matches`, `auth_error`) — and report
that blocker.

NeqSim supports an AI-driven task-solving workflow. When asked to solve an
engineering task (hydrate prediction, pipeline sizing, compressor design, etc.):

#### ⚠️ MANDATORY: All output goes to `task_solve/` folder

**Every task MUST create a folder under `task_solve/` FIRST.** All deliverables
(task_spec.md, notebooks, notes.md, results.json, figures/) are placed inside
this folder. Never write task analysis files to `examples/`, docs, or the
workspace root.

#### ⚠️ MANDATORY: All downloaded documents go INSIDE the task folder

**All documents retrieved during a task — STID drawings, PI historian exports,
vendor datasheets, P&IDs, literature PDFs — MUST be saved to
`step1_scope_and_research/references/` within the task folder.** Never save
task-related files to workspace-level directories like `output/` or `figures/`.
Converted PNGs go to the task's `figures/` directory. This ensures tasks are
self-contained and portable.

**File them into per-source subfolders + generate a distributable `SOURCES.md`
(MANDATORY).** So the collected set can be handed to others and reused, place
each document under `references/` in a **per-source subfolder** and keep a
human-readable index:

```
references/
├── SOURCES.md              # human-readable summary of everything collected
├── collection_manifest.json    # machine-readable record (auto-generated)
├── stid/  pepr/  tr2000/  maintenance/  servicenow/
├── tagreader/  seeq/  rigga/  vendor/  lab/  literature/  web/  manual/
```

Run the dependency-free generator to organize loose files into source folders
and (re)build `collection_manifest.json` + `SOURCES.md`:

```bash
python devtools/generate_sources_md.py task_solve/YYYY-MM-DD_slug --organize
```

`SOURCES.md` lists, per source, each file with its origin (document number /
tag / action ID / RITM / historian tag), retrieval date, classification,
relevance, review status, and a one-line summary, plus a data-gaps section.
Regenerate it whenever documents are added and before finalizing the task.

#### Step-by-step

> **If a terminal reports `neqsim` is not recognized, do not improvise and do not
> skip the step.** The console script is simply not on PATH in that shell. Re-run
> the identical command as `<python-executable> -m neqsim_cli ...`, where
> `<python-executable>` is the interpreter named under "Python Environment
> Reuse" above — same entry point, same arguments. This applies to every
> `neqsim ...` command in this document.

1. **Create the task folder (DO THIS FIRST — non-negotiable):**
   ```bash
   neqsim new-task "your task title" --type B --author "Name"
   ```
   Types: A=Property, B=Process, C=PVT, D=Standards, E=Feature, F=Design, G=Workflow

2. **Read the generated README** at `task_solve/YYYY-MM-DD_task_slug/README.md`

3. **Follow the 3-step workflow:**

   **Step 1 — Scope & Research**
   - Fill `step1_scope_and_research/task_spec.md` (standards, methods, deliverables, acceptance criteria)
   - **Run `@capability-scout` and write the result to** `step1_scope_and_research/capability_assessment.md` (mandatory for Standard/Comprehensive). The validator will warn if this artifact is missing or unfilled.
   - **Discover the right skills** via semantic search:
     ```bash
     python devtools/skill_search.py "<your task title>" --top 5
     ```
     Load the top-3 skills before starting analysis.
   - **Discover the best agents** across all repos (neqsim + community +
     enterprise) and plan the workflow — do not rely only on `router.agent.md`:
     ```bash
     python devtools/agent_search.py "<your task title>" --top 8 \
         --json --out step1_scope_and_research/agent_plan.json
     ```
     Record the chosen agents in `capability_assessment.md` §4b and the
     composition (single agent / router pattern / declarative `composeWorkflow`
     or `engineering-harness` study) in §4c. Prefer delegating to a specialist
     agent over re-loading its skills so its governance/workflow is reused, and
     copy the plan into `results.json` `agent_workflow_plan` for the report.
   - **Pull literature and internal docs** via `@literature-scout`. PDFs land in `step1_scope_and_research/references/` (in per-source subfolders, e.g. `literature/`, `stid/`); manifest in `references/collection_manifest.json`; summaries appended to `notes.md`.
   - Write **substantive** research notes to `step1_scope_and_research/notes.md` (no empty template sections)
   - Place literature papers, standards PDFs, and lab reports in `step1_scope_and_research/references/` under the matching source subfolder (`literature/`, `lab/`, `stid/`, `vendor/`, `manual/` ...)
   - **Organize + summarize the collection** by running `python devtools/generate_sources_md.py task_solve/YYYY-MM-DD_slug --organize` — it files loose docs into per-source folders and writes the distributable `references/SOURCES.md` + `references/collection_manifest.json`. Rerun whenever new documents are added.
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
   - Run `neqsim report <task_dir>` to produce a professional engineering report
     (Word + HTML). Never open, edit or copy `generate_report.py` (~320 KB);
     hand-written prose goes in `step3_report/report_sections.json`
     (`manual_sections`: executive_summary, problem_description, approach,
     conclusions, references; plus `doc_number`, `revision`, `revision_history`)
   - **PDF:** add `pdf` to `report.formats` in `study_config.yaml`, or pass
     `--pdf` (`--no-pdf` overrides the config). The PDF is rendered from the
     DOCX, not the HTML, so it inherits the configured Word template; it needs
     Microsoft Word with pywin32, or LibreOffice on PATH. A conversion failure
     is reported and does not abort the report run.
   - **The report title is the study title, and the report FILES are named after
     it.** Set `study.title` in `study_config.yaml` (optionally `study.author`,
     `study.classification`); override per run with `--title` / `--author`.
     Without it the generator falls back to the `task_spec.md` heading, then the
     folder name. A study titled "Hydrate margin for the export line" ships
     `step3_report/Hydrate_margin_for_the_export_line.docx` (+ `.html`), so the
     deliverable is identifiable outside its task folder. Report files written
     under an earlier title are deleted on regeneration — never keep a
     superseded `Report.docx` beside the current one.
   - **The task is stated at the top of the report**, before any analysis, from
     `results.json` `task_statement`/`objective`, else the `## Objective`
     section of `task_spec.md`. Fill one of them — otherwise the report can only
     restate the study title.
   - **The work record is generated automatically with the report** — every
     `generate_report.py` / `neqsim report` run also writes
     `step3_report/WORK_RECORD.md`: what was done, how, with which data, every
     script/notebook and its outputs, every source system and collected document,
     an annotated folder map, and the commands to reproduce the study. The report
     carries the conclusion; the work record carries the method and provenance,
     so a reader who was not in the conversation can audit or repeat the task.
     Fill the three NARRATIVE blocks (`background`, `method`, `limitations`) by
     hand — they survive regeneration. Rebuild it alone with
     `neqsim work-record <task folder>`; check with `--check`; enforce with
     `report.work_record: required`, or opt out with `skip`, in `study_config.yaml`.
   - The Word report follows the user's Word template when one is configured
     (`neqsim --set-report-template "PATH"`, `NEQSIM_REPORT_TEMPLATE`, or
     `--template PATH` for a single run), inheriting their organisation's styles,
     fonts, headers, and footers. Report a missing-template error rather than
     issuing an unbranded report.
   - **Report language:** English by default. A task written in another language
     sets `report.language` in `study_config.yaml` (`nb`/`no` for Norwegian, or
     any ISO code); `--language CODE` / `NEQSIM_REPORT_LANGUAGE` override it for
     one run. It translates the generator's own headings, cover labels, and
     caption prefixes and sets the document language for spell-check — write
     `results.json` and `task_spec.md` content in the same language. The
     scientific paper (`--paper`) stays English.
   - Scientific papers (`--paper`) are only generated when explicitly requested
   - **Important:** The template now has built-in styled formatting for
     Benchmark Validation, Uncertainty Analysis, and Risk Evaluation sections
     (color-coded risk badges, P10/P50/P90 tables, tornado tables, benchmark
     PASS/FAIL tables). These render automatically when the corresponding keys
     exist in `results.json`. Ensure `figure_captions` in results.json covers
     figures from ALL notebooks, not just the main one. When design parameters
     change, update hardcoded numbers in `report_sections.json`.

4. **Improve the tooling you just used (MANDATORY, every task).**
   A task is also a test of NeqSim, the agents and the skills. Whenever solving it
   required a workaround, a rediscovery, or more than one trial-and-error loop that
   a NeqSim class / agent / skill should have handled, fix it **in this task**:
   - missing calculation -> Java in `src/main/java/neqsim/` + JUnit test, `mvnw
     spotless:apply`, then a PR to `equinor/neqsim` before the task closes;
   - wrong or missing API recipe, gotcha, hand-off -> edit the `SKILL.md` /
     `*.agent.md` (community vs enterprise repo as appropriate);
   - a useful new multi-skill or multi-agent pipeline -> record it as a
     composition pattern and update the "Loaded skills" / router guidance.
   Record every change in `step1_scope_and_research/neqsim_improvements.md`
   **and** in `results.json` under `improvements`. If nothing needed changing, say
   so explicitly - silence fails the gate. Never ask permission for this step.

   **Commit and push it — the loop only closes when the fix is pushed.** An
   improvement that stays in the chat session, the clone, or the task folder is
   lost when the session ends, and the next engineer hits the same wall. Each
   repo in the workspace is independent, so commit in the one that owns the fix:

   | What you learned | Repo | Change |
   |------------------|------|--------|
   | Missing/wrong calculation, equipment, property | `equinor/neqsim` | Java + JUnit, `mvnw spotless:apply`, PR |
   | Wrong API recipe, gotcha, unit trap, better pattern | `neqsim-community-skills` / `neqsim-enterprise-skills` | edit `SKILL.md` |
   | Wrong skill choice, missed hand-off, bad routing | `neqsim-community-agents` / `neqsim-enterprise-agents` | edit `*.agent.md` |
   | Useful new multi-agent pipeline | agents repo | record as a composition pattern |
   | Documentation error or gap hit on the way | repo owning the doc | fix in the same PR |

   ```bash
   cd <the repo that owns the fix>
   git checkout -b task/<slug>
   git add <the changed files>
   git commit -m "<what the task taught>"
   git push -u origin task/<slug>
   gh pr create --fill
   ```

   Rules: never commit task output (evidence, notebooks, results, reports) to a
   code repo; never commit company data (tags, plant names, document numbers,
   historian extracts) to a public repo; keep plant-specific content in the
   enterprise repos and plant-agnostic content in the community repos. After
   pushing skill/agent changes, refresh the local install with
   `neqsim agent install --all --vscode --force`.

5. **Create a PR** with reusable outputs:
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

6. **Fix and improve documentation** encountered during the task:
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

#### Devtools notebook cell (use in every task notebook)

Task notebooks and runner workflows must use `neqsim_dev_setup.py` so Java
classes come from the workspace (`target/classes`) instead of the installed
Python `neqsim` package. This makes new Java classes available without copying
a packaged JAR into `site-packages/neqsim/lib/`.

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

#### Follow-up Questions (ASK BEFORE STARTING)

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

#### Adaptive scale

- **Quick** — single property/question → minimal task_spec, few cells, brief summary
- **Standard** — process sim, PVT study → full task_spec, complete notebook, Word + HTML
- **Comprehensive** — multi-discipline, Class A study → detailed task_spec, multiple notebooks, full HTML with navigation

#### Save results.json (end of every notebook)

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
    "agent_workflow_plan": {
        # How the task was solved — record the discovered/used agents & workflow
        # (from devtools/skill_search.py + devtools/agent_search.py). Feeds the report.
        # "agents_used": [{"name": "process.model", "repo": "neqsim", "role": "build flowsheet",
        #                  "loads_skills": ["neqsim-process-modeling"]}],
        # "workflow_type": "composition_pattern",
        # "workflow": "process.model -> mechanical.design",
        # "rationale": "..."
    },
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

#### Iterative Updates to results.json

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
- **Information Sources and Evidence Basis** (no results.json key needed): built from `step1_scope_and_research/references/collection_manifest.json` — how many documents came from each source system (STID, SAP/Maintenance, PEPR, TR2000, historian, vendor, literature …), plus the `inputs.data_sources` systems read with their captured-evidence paths, plus documents sought but not obtained. Run `python devtools/generate_sources_md.py <task> --organize` so the manifest exists.
- **assumptions / data_gaps** (also accepted: `assumptions_and_gaps: {assumptions: [], data_gaps: []}`, `evidence_gaps`, `assumptions_gaps`): rendered as the **Assumptions and Data Gaps** section. Assumptions may be plain strings, or dicts with `assumption` / `basis` / `effect`. Data gaps should be dicts with `gap` (or `blocker`), `source`, `status`, `assumed` (what was used in its place) and `effect`. The gate warns when a declared data source has no captured evidence but nothing is registered here — a study that could not get a document still reached an answer somehow, and that substitution must be visible.
- **equations**: KaTeX in HTML, PNG images in Word
- **figures**: Numbered captions from `figure_captions`
- **figure_discussion**: Discussion blocks with observation, mechanism, implication, recommendation — rendered as a "Discussion" section (report) or inline in "Results and Discussion" (paper). Links figures to conclusions via traceability chain.
- **tables**: Custom tables rendered in both HTML and Word with headers/rows
- **references**: Numbered reference list rendered in the References section of the report
- **uncertainty**: Monte Carlo results (P10/P50/P90, tornado data, probability of negative outcome) rendered as styled tables in the Uncertainty Analysis section
- **risk_evaluation**: Risk register with color-coded risk levels (High=red, Medium=orange, Low=green), summary badges, and mitigation table in the Risk Evaluation section
- **benchmark_validation**: Benchmark tests rendered as a table with PASS/FAIL color coding and detail columns

### Code Patterns

#### Create a fluid

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic"); // NEVER skip
```

#### Run a flash

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();  // MANDATORY: initializes both thermodynamic AND transport properties
// NOTE: init(3) alone does NOT initialize transport properties (viscosity, thermal conductivity)
double density = fluid.getDensity("kg/m3");
double viscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double thermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
```

#### Phase envelope branch labels

`getDewPointTemperatures()` / `get("dewT")` contain the physical dew branch and
`getBubblePointTemperatures()` / `get("bubT")` contain the physical bubble branch,
including when `calcPTphaseEnvelope(true, 1.0)` starts from the bubble side.
Do not swap the getters based on trace order. Flat arrays can contain paired
NaN separators between disjoint segments; use the structured segment API for
finite points without separators. No converged points raises an
`IllegalStateException`; pressure/point-limit truncation also throws and leaves
extrema as NaN. The input state is never substituted for an extremum.

#### Process simulation

```java
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
Separator sep = new Separator("HP sep", feed);
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(sep);
process.run();
```

#### Automatic recycle insertion (`makeRecycles`)

Do not hand-write a tear stream + `Recycle` per feedback stream. A loop wired
straight back into an upstream mixer has no tolerance, acceleration or convergence
report of its own; across `ProcessModel` areas it is closed only by the outer
Gauss-Seidel pass, which has no relaxation setting.

```java
List<Recycle> created = process.makeRecycles();   // SCCs of one flowsheet
List<Recycle> plantRecycles = plant.makeRecycles(); // cross-area streams, then each area
plant.setAutoRecycles(true);  // or let run()/runUntilConverged(...) do it
```

Tears the inlet with the smallest recycle ratio (tear flow / total inlet flow of the
consuming unit), one edge per round, and tunes each generated `Recycle` with
`setAdaptiveAcceleration(true)` plus an absolute flow tolerance at 1e-6 of the area's
largest flow. Self-seeding and idempotent. Only `Mixer` / `Manifold` inlets are
tearable; other loops are logged and left untouched. `setAutoRecycles` defaults to
**false** because inserting a tear changes how an existing flowsheet iterates.
See `docs/process/controllers.md#automatic-recycle-insertion`.

#### Separator mechanical design (physical configuration)

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

For JSON-driven CAD or external design calculations, use `design.toDesignDataJson()`
after running the process and `calcDesign()`. Read each quantity's unit, source and
availability; legacy wall-thickness getters use m for vessels/compressors but mm
for pumps/pipelines/columns. Check `geometryConsistency`, and keep compressor
envelope dimensions separate from pressure-casing calculation results. See
`docs/process/mechanical_design.md` and `MechanicalDesignJsonContractTest`.

**Internals classes** (`mechanicaldesign.separator.internals`):
- `DemistingInternal` — Eu-number pressure drop, Souders-Brown max velocity,
  carry-over model for wire mesh / vane pack / cyclone demisting devices
- `DemistingInternalWithDrainage` — adds drainage section efficiency

**Primary separation** (`mechanicaldesign.separator.primaryseparation`):
- `PrimarySeparation` — inlet momentum, bulk separation, carry-over
- `InletVane` (6000 Pa, 85%), `InletVaneWithMeshpad` (92%+mesh),
  `InletCyclones` (8000 Pa, 95%)

#### Stream introspection

Every `ProcessEquipmentInterface` exposes its connected streams:

```java
List<StreamInterface> inlets = sep.getInletStreams();   // [feed]
List<StreamInterface> outlets = sep.getOutletStreams();  // [gasOut, liquidOut]
```

#### Named controllers

Attach multiple controllers to any equipment by tag name:

```java
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);
ControllerDeviceInterface lc = valve.getController("LC-100");
Map<String, ControllerDeviceInterface> all = valve.getControllers();
```

#### Explicit connections

Record typed connection metadata on a `ProcessSystem`:

```java
process.connect(feed, sep,
    ProcessConnection.ConnectionType.MATERIAL, "Feed");
List<ProcessConnection> conns = process.getConnections();
```

#### Unified element query

`ProcessElementInterface` is the common supertype for equipment, controllers,
and measurement devices. Query all elements at once:

```java
List<ProcessElementInterface> all = process.getAllElements();
```

#### Automation API (PREFERRED for agents)

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

// Batch operations (one run after all writes)
Map<String, Double> updates = new LinkedHashMap<String, Double>();
updates.put("Compressor.outletPressure", 150.0);
updates.put("Valve.outletPressure", 45.0);
auto.setValues(updates, "bara", true);  // runAfter=true → single run() at end

Map<String, Double> values = auto.getValues(
    Arrays.asList("HP Sep.pressure", "Compressor.outletPressure"), "bara");

// Dirty tracking — agents avoid redundant run() calls
auto.setVariableValue("Compressor.outletPressure", 160.0, "bara");
if (auto.isDirty()) auto.runIfDirty();   // runs once and clears flag
auto.setVariableValueAndRun("Cooler.outletTemperature", 30.0, "C");  // set + run + clear

// Discovery and introspection
String manifest = auto.describe();           // JSON: schemaVersion, units, variables, types
String snap = auto.snapshot("HP Sep");       // JSON snapshot of a unit / area / "*"
String topo = auto.getTopology();            // JSON: equipment + connections
String adj = auto.getNeighbors("Cooler");    // upstream / downstream units

// Structured (non-scalar) reads — composition, components, phaseFractions, kvalues
JsonElement comp = auto.getStructured("HP Sep.gasOutStream.composition");
// → {"methane": 0.85, "ethane": 0.10, ...}

// Non-throwing pre-flight validation
AutomationDiagnostics.DiagnosticResult issue = auto.validateAddress("HP Sep.foo");
if (issue != null) { /* handle without try/catch */ }

// UOM hints
List<String> uoms = auto.getAllowedUnits("HP Sep.pressure");  // ["bara","Pa","psia","barg"]

// Stable JSON schema for all *Safe / describe / snapshot / topology outputs
String SCHEMA = ProcessAutomation.SCHEMA_VERSION;  // "1.0"
```

**Cached facade:** `process.getAutomation()` returns the same instance on every call so
the diagnostics history, learned corrections, and dirty flag persist across agent turns.

For multi-area `ProcessModel`:

```java
ProcessAutomation plantAuto = plant.getAutomation();
List<String> areas = plantAuto.getAreaList();  // ["Separation", "Compression"]
// Area-qualified addresses
double t = plantAuto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
plantAuto.setVariableValue("Compression::Compressor.outletPressure", 170.0, "bara");
plant.run();
```

#### Closed-loop optimization: `evaluate()` (PREFERRED for agent loops)

`run()` returns `void`, so an agent that calls it must separately inspect `RunStatus`,
`solved()`, and the convergence report to decide whether a trial is usable. The agentic run
primitives collapse that into **one schema-versioned JSON object that never throws** — the single
most important addition for closed-loop work. **Use `evaluate()` as the atomic optimizer step**:
it applies a batch of setpoints, runs to convergence, gates feasibility, and reads back objectives.

```java
ProcessAutomation auto = plant.getAutomation();

Map<String, Double> setpoints = new LinkedHashMap<String, Double>();
setpoints.put("Compression::Export Compressor.outletPressure", 150.0);
setpoints.put("Separation::Oil Heater.outletTemperature", 78.0);
List<String> readbacks = Arrays.asList("Compression::Export Compressor.power");

// Mixed-unit batch → pass null to use each variable's default unit (bara, K, kg/hr).
// Single-unit batch → pass the shared unit (e.g. "bara") for setpoints and read-backs.
String json = auto.evaluate(setpoints, null, readbacks, "kW", 30, 5.0e-3);
// {"schemaVersion":"1.0","setpointsApplied":{...},"setpointsRejected":{},
//  "runSucceeded":true,"converged":true,"iterations":7,"maxError":0.0021,
//  "failedUnitName":null,"feasible":true,"readbacks":{...},"readbackErrors":{}}

// Convenience overload: same unit for setpoints+readbacks, defaults (30 iter, tol 5e-3)
String quick = auto.evaluate(setpoints, "bara", readbacks);
```

Gate optimizer trials on the single **`feasible`** flag — it is `true` only when the run did not
throw, the model converged, no unit failed, and **every** setpoint was accepted. A bad address or
out-of-bounds value lands in `setpointsRejected` (good setpoints still applied); a bad read-back
lands in `readbackErrors` — both without throwing, so one malformed candidate degrades a single
trial instead of crashing the loop. Through jpype, wrap as `json.loads(str(result))`.

Lower-level gated runs (when you set values yourself):

```java
String conv = auto.runUntilConvergedJson(30, 5.0e-3); // convergence report + run status (never throws)
String run  = auto.runJson();                          // single run() with structured outcome
String last = auto.getRunStatusJson();                 // last run status without re-running
```

For a multi-area `ProcessModel`, `runUntilConvergedJson()` / `evaluate()` delegate to
`ProcessModel.runUntilConverged(...)` and embed the nested `convergence` report plus a per-area
`areas` array. For a single `ProcessSystem`, `run()` already iterates internal recycles and
`converged` reflects the `RunStatus` success flag. All three primitives clear the dirty flag even
on failure. Default tolerance `5e-3` is robust for plants with near-zero-flow anti-surge recycles
(strict `1e-4` rarely converges there). Pair with `getAdjustableParameters()` to enumerate the
bounded decision space the agent may perturb.

#### Capacity observation snapshot: `getUtilizationSnapshot()` (the observation vector)

`evaluate()` is the **action + reward** step; `getUtilizationSnapshot()` is the matching
**observation** step. Both `ProcessSystem` and `ProcessModel` expose
`getUtilizationSnapshotJson()`, and `ProcessAutomation.getUtilizationSnapshot()` delegates to
whichever it wraps. The snapshot is **side-effect-free** — it never calls `run()`, only reads the
capacity utilization already computed by each unit's `CapacityConstraint`s. Units are emitted in
insertion order so the observation vector is deterministic across calls.

```java
ProcessAutomation auto = plant.getAutomation();
plant.run();                                   // (or auto.evaluate(...)) — snapshot reflects last solve
String json = auto.getUtilizationSnapshot();
// {"schemaVersion":"1.0",
//  "units":[{"area":"Compression","name":"Export Compressor","type":"Compressor",
//            "capacityAnalysisEnabled":true,"maxUtilization":0.83,"maxUtilizationPercent":83.0,
//            "limitingConstraint":"power","feasible":true,"hardLimitExceeded":false,
//            "power_kW":1240.5,"constraints":[{"name":"power","utilization":0.83,...},...]}, ...],
//  "bottleneck":{"name":"Export Compressor","utilization":0.83,"utilizationPercent":83.0,
//                "limitingConstraint":"power"},
//  "anyOverloaded":false,"anyHardLimitExceeded":false}
```

Per unit: `name`, `type`, `capacityAnalysisEnabled`, `maxUtilization` (0–1, NaN→0),
`maxUtilizationPercent`, `limitingConstraint` (or `null`), `feasible`, `hardLimitExceeded`,
`power_kW` (compressors/pumps only), and a `constraints[]` breakdown (`name`, `utilization`,
`current`, `design`, `unit`, `enabled`, `violated`, and `dataSource` when set — e.g. `"equipment"`,
`"design"` — so an agent can tell a rated limit from an estimate). For a `ProcessModel`, every unit
also carries its `area` label. Plant-wide: `bottleneck` (highest-utilization unit, or `null`),
`anyOverloaded`, `anyHardLimitExceeded`.

**Closed-loop RL pattern:** observation = `getUtilizationSnapshot()`; action = setpoints passed to
`evaluate()`; reward = an objective read-back from `evaluate()` (e.g. negative compression power)
**penalized when** `anyOverloaded` is `true` or any unit's `maxUtilization > 1`. Because the
snapshot reads constraints rather than re-solving, it is cheap to call on every step.

> **Compressors without a performance chart**: the chart-dependent constraints (surge, stonewall,
> speed) are now **present but disabled** for chartless compressors (their distance-to-surge is
> undefined and would otherwise pin utilization at a degenerate flat 100%). Such a compressor
> reports smooth, power-driven utilization. Set an installed shaft power via
> `comp.getMechanicalDesign().setMaxDesignPower(kW)` to give the `power` constraint a basis. When a
> chart is later attached, `reinitializeCapacityConstraints()` re-enables the chart metrics.

> **Expanders (turbo-expanders)**: `Expander` overrides the inherited `Compressor` capacity logic
> so it no longer reports a spurious ~150% utilization. The inherited consumed-power constraints
> (`power`, `ratedPower`) are removed and `isSimulationValid()` is expander-correct (negative shaft
> power and a cooler outlet are *valid*). Call `expander.setRatedRecoveredPower(ratedKW)` to add a
> `recoveredPower` HARD constraint (sourced from `|getPower|`, `dataSource = "equipment"`); without
> a rating the expander simply reports no spurious limit instead of a fabricated one.

#### AgenticProcessOptimizer: closed-loop optimization for ML/agentic loops

`evaluate()` makes a flowsheet *steppable*; `AgenticProcessOptimizer` is the **ready-made search
loop** built on top of it, purpose-designed for ML and agentic use. Get one with
`auto.newOptimizer()` (or `new AgenticProcessOptimizer(automation)`). It works entirely in terms of
**string addresses**, a **never-throwing schema-versioned JSON contract**, and a **replayable
trajectory** — so an LLM agent can build and solve an optimization problem straight from
`getAdjustableParametersJson()` output without navigating Java objects.

```java
ProcessAutomation auto = plant.getAutomation();
AgenticProcessOptimizer opt = auto.newOptimizer();
opt.addVariable("Compression::Export Compressor.outletPressure", 80.0, 200.0, "bara");
opt.minimize("Compression::Export Compressor.power", "kW");
opt.addConstraintLessOrEqual("Export Oil.RVP", 0.79, "bara", 1.0e4); // quadratic penalty
opt.setSeed(42).setMaxEvaluations(80);
AgenticProcessOptimizer.OptimizationResult result = opt.optimize(); // never throws
String json = opt.optimizeToJson(); // schema-versioned JSON incl. trajectory
```

- **Algorithm**: bounded Nelder–Mead simplex with deterministic (seeded) random initialization. A
  flowsheet behind `evaluate()` is a noisy, feasibility-gated black box with no usable analytic
  gradient, so a derivative-free method is the right choice. Same seed + same problem ⇒ identical
  trajectory (reproducible experiments).
- **Decision space**: `addVariable(address, lo, hi, unit)` (per-variable unit), or
  `useAdjustableParameters()` to auto-fill bounded variables from the process adjusters (returns the
  count added, skips unbounded ones).
- **Objective**: `minimize(addr, unit)` / `maximize(addr, unit)` / `setObjective(addr, Sense, unit)`
  for an address-based goal, or `setObjectiveFunction(Function<Map<String,Double>,Double>)` for a
  **custom reward** computed over a read-map of decisions + constraint read-backs + watches
  (reward shaping). Add observables with `addWatch(addr, unit)`.
- **Constraints**: `addConstraintLessOrEqual` / `addConstraintGreaterOrEqual` /
  `addConstraint(addr, type, limit, unit, penaltyWeight)` — hard inequalities folded in as weighted
  quadratic penalties; infeasible runs are still logged but pushed to the back with a large penalty.
- **Per-trial gating**: each trial sets the decision variables (each in its own unit, catching
  rejections), calls `evaluate()` for one gated run (apply → run to convergence → feasibility flag),
  then reads the objective/constraints individually. A malformed candidate degrades **one** trial
  instead of crashing the loop; `optimize()` itself never throws.
- **Trajectory tape**: every evaluated point is logged as a `Trial` (setpoints, read-backs, raw
  objective, penalty, feasibility, minimized score) — the (state, action, reward) tape for offline
  RL, surrogate-model fitting, and agent post-mortems. Exposed via `result.getTrajectory()` and in
  the result JSON.
- **Self-rating**: `getReadinessJson()` returns a machine-readable self-assessment rating each
  capability `full`/`partial`/`none` (never_throws=full, deterministic=full,
  bounded_action_space=full, json_io=full, reward_shaping=full, constraint_handling=full,
  trajectory_logging=full, feasibility_gating=full; gradient_based=none,
  global_optimum_guarantee=partial, parallel_evaluation=none) so an agent can decide whether to use
  it before committing a budget.
- **Tuning**: `setMaxEvaluations(int)` (evaluation budget), `setInnerConvergence(maxIter, tol)`
  (per-trial `evaluate()` gating), `setConvergenceTolerance(double)` (simplex stop), `setSeed(long)`.

#### Capacity / throughput / quality / batch helpers on `ProcessAutomation`

These string-addressable, never-throwing, schema-versioned JSON helpers close the loop for
maximise-production studies and work for **both** a `ProcessSystem` and a `ProcessModel`:

- **`enableCapacityConstraints()` — capacity for ALL equipment types.** Enables the capacity
  constraints on every `CapacityConstrainedEquipment` in the flowsheet (separators, pumps, valves,
  pipelines, heaters/coolers, heat exchangers, manifolds, …) so any of them can bind as the
  bottleneck in `getUtilizationSnapshot()` / `getBottleneckRankingJson()` / `findMaxThroughputJson()`.
  It deliberately does **not** call the blind `enableAllConstraints()` (that would re-enable the
  degenerate surge/speed constraints on chartless compressors); instead it recreates compressor
  constraints via `reinitializeCapacityConstraints()` (surge/speed stay disabled when chartless,
  power stays enabled) and adds the separator Souders-Brown gas-load constraint. Set each type's
  design basis first (`comp.getMechanicalDesign().setMaxDesignPower(kW)`, pump/valve/pipe limits,
  `sep…setGasLoadFactor(K)`). Returns the separator count.
- **`findMaxThroughputJson(feedAddresses, minRate, maxRate, rateUnit, utilizationLimit)` — native
  max-throughput-at-capacity.** Enables the capacity constraints, then bisects the *total* feed rate
  (all feeds scaled proportionally to their base rate) until the first unit's `maxUtilization`
  reaches `utilizationLimit` (a 0–1 fraction), leaving the model at the feasible maximum. Returns
  `{maxRate, rateUnit, feasibleAtMin, bindingUnit, bindingConstraint, bindingUtilizationPercent}`.
- **`getProductQualityJson(address[, refTempC])` — product-quality observables.** For a resolved
  stream (area-qualified `Area::Unit`, `unit.port`, or a bare unit → its first outlet), returns the
  export-oil RVP/TVP (`rvp_bara`, `tvp_bara` via `Standard_ASTM_D6377`) and gas `cricondenbar_bara` /
  `cricondentherm_K` (via `calcPTphaseEnvelope`), each on a cloned fluid so the live model is
  untouched. Never throws — an uncomputable metric is reported as `rvpError` / `envelopeError`. Use
  these as the spec side of a maximise-throughput-subject-to-RVP/cricondenbar optimisation.
- **Routing / feed-scale decision variables.** Feed streams expose `flowRate` as a writable INPUT
  (feed-scale). Splitters expose one bounded `splitFactor_i` (0–1) INPUT per outlet in
  `getAdjustableParameters()` — the routing decision variables. Reading `<Splitter>.splitFactor_i`
  returns the current fraction; writing it sets that branch's relative weight and the splitter
  renormalises the factors to sum to 1. Because they carry `[0,1]` bounds,
  `AgenticProcessOptimizer.useAdjustableParameters()` picks them up automatically. (Give a feed
  `flowRate` explicit bounds to make feed-scale a decision variable too.)
- **`evaluateBatchJson(candidates, unit, readbacks, maxParallel)` — parallel batch for ML / DoE.**
  Scores a *list* of setpoint maps in one call. For a `ProcessSystem` with `maxParallel > 1` it
  evaluates each candidate on an independent `ProcessSystem.copy()` on its own thread, so the batch
  is genuinely parallel and the **live model is left untouched** (ideal for SciPy/BoTorch/GA/agent
  populations). For a `ProcessModel` (no `copy()`) or `maxParallel == 1` it runs sequentially on the
  live facade. Each result carries the full `evaluate` payload — including the
  `converged` / `iterations` / `maxError` / `failedUnitName` / `failedUnitError` convergence-failure
  detail — plus its `index`; the root reports `parallel`, `feasibleCount`, `firstFeasibleIndex`.
- **Maximise production *and* manage emissions (composition pattern).** Decision space =
  `getAdjustableParameters()` (bounded setpoints + splitter routing; bound feed `flowRate` for
  feed-scale); feasibility = `enableCapacityConstraints()` + the capacity snapshot; objective = an
  `AgenticProcessOptimizer.setObjectiveFunction` reward such as `production − λ·Σ(compressor power)`
  (compression shaft power is a direct CO2 proxy for turbine-driven trains), or
  `ProductionOptimizer.optimizePareto` with `[MAXIMIZE production, MINIMIZE Σ compressor power]` for
  the production-vs-emissions trade-off front. `evaluateBatchJson` read-backs of each compressor's
  `power` give the emissions term per candidate for an external Python optimizer.

#### Self-healing automation (PREFERRED for agents)

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

#### Lifecycle state: save, restore, compare

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

#### Python (Jupyter) fluid in task notebooks

```python
fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.setMixingRule("classic")
```

#### Headless execution (no kernel restarts)

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

#### Task progress checkpoints (survives context exhaustion)

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

#### Build process from JSON

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
  logger.warn("{}: {}", w.getCode(), w.getMessage());
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

#### Subsea well design (mechanical design + cost)

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

#### Equipment design feasibility reports

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

#### CO2 injection well analysis

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

#### Python (Jupyter) — CO2 well analysis

```python
CO2InjectionWellAnalyzer = ns.JClass("neqsim.process.equipment.pipeline.CO2InjectionWellAnalyzer")
TransientWellbore = ns.JClass("neqsim.process.equipment.pipeline.TransientWellbore")
CO2FlowCorrections = ns.JClass("neqsim.process.equipment.pipeline.CO2FlowCorrections")
ImpurityMonitor = ns.JClass("neqsim.process.measurementdevice.ImpurityMonitor")
```

### Key Paths

| Path | Purpose |
| --- | --- |
| `src/main/java/neqsim/` | Main source (thermo, process, pvt, standards) |
| `src/test/java/neqsim/` | JUnit 5 tests (mirrors src structure) |
| `src/main/java/neqsim/process/equipment/` | ProcessEquipmentInterface, MultiPortEquipment, stream introspection |
| `src/main/java/neqsim/process/processmodel/` | ProcessSystem, ProcessConnection, ProcessElementInterface, JsonProcessBuilder, SimulationResult |
| `src/main/java/neqsim/process/automation/` | ProcessAutomation (string-addressable variable API), AutomationDiagnostics (fuzzy matching, auto-correction, physical validation, learning), SimulationVariable (INPUT/OUTPUT descriptor) |
| `src/main/java/neqsim/process/processmodel/lifecycle/` | ProcessSystemState, ProcessModelState — JSON lifecycle snapshots, version comparison, compressed transfer |
| `devtools/unisim_reader.py` | UniSim COM reader → NeqSim Python/notebook/EOT/JSON (UniSimReader, UniSimToNeqSim, UniSimComparator). 45+ typed operation handlers via `UniSimOperationHandler`, port-specific forward refs, auto-recycle wiring. **Default E300 fluid export**: `read(export_e300=True)` extracts Tc, Pc, omega, MW, BIPs from COM and writes E300 files for all fluid packages. `build_and_run()` auto-loads E300 fluids via `EclipseFluidReadWrite.read()` and `ProcessSystem.fromJsonAndRun(json, fluid)`. Generated JSON includes `_unisim_operation_mapping` for native/adapter/reference/control/internal/skip traceability. **Full mode default**: `full_mode=True` (all 4 methods) auto-classifies sub-flowsheets as process/utility, includes only process SFs in ProcessModel. Verified with TUTOR1.usc (11/13 streams match) and R510 SG Condensation (31 comp, 250 ops, 8 SFs: 78% isolated match, 71% connected). |
| `devtools/test_unisim_outputs.py` | Pure-Python tests for UniSim converter output modes, E300 fluid export, operation handler registry strategy, and JSON mapping summaries (no COM needed — synthetic models) |
| `examples/notebooks/tutor1_gas_processing.ipynb` | End-to-end UniSim→NeqSim verification: TUTOR1 gas processing (7 comp, PR EOS, 13 ops). Reference for conversion workflows. |
| `src/main/java/neqsim/process/mechanicaldesign/subsea/` | Well & SURF design, cost estimation |
| `src/main/java/neqsim/process/mechanicaldesign/` | Engineering deliverables (StudyClass, InstrumentScheduleGenerator, etc.) |
| `src/main/java/neqsim/process/mechanicaldesign/heatexchanger/` | HX thermal-hydraulic design (ThermalDesignCalculator, BellDelawareMethod, VibrationAnalysis) |
| `src/main/java/neqsim/process/equipment/subsea/` | SubseaWell, SubseaTree equipment |
| `src/main/java/neqsim/process/equipment/pipeline/` | Pipe flow, TwoFluidPipe, CO2InjectionWellAnalyzer, TransientWellbore |
| `src/main/java/neqsim/process/measurementdevice/` | Transmitters (PT, TT, LT, FT, DifferentialPressureTransmitter), CompositionAnalyzer, FlowRatioMeter, AlarmConfig, ImpurityMonitor |
| `src/main/java/neqsim/process/dynamics/` | Pluggable transient integrators (`IntegratorStrategy`, `ExplicitEulerIntegrator`, `BDFIntegrator`) and `EventScheduler` (ESD trips, setpoint ramps) wired into `ProcessSystem.runTransient` and `ProcessModel.runTransient` |
| `src/main/java/neqsim/process/synthesis/` | `SeparationDuty` + `FlowsheetSynthesisEngine` for agentic flowsheet generation |
| `src/main/java/neqsim/process/safety/selfheating/` | Self-heating criticality — `PorousMediaSelfHeatingAnalyzer` (Frank-Kamenetskii), `SemenovSelfHeatingAnalyzer`, `SelfHeatingInductionSolver` (time to ignition), `BasketTestRegression` (EN 15188 / ASTM E2021 kinetics fitting). Distinct from the lumped-adiabatic `RunawayReactionAnalyzer` |
| `src/main/java/neqsim/process/safety/firewater/` | Fire-water / deluge coverage design — `FireWaterDemandCalculator` (area + dedicated object rates, simultaneous-release factor, AFFF), `DelugeNozzleLayout` (nozzle count from **both** the flow and the spray-overlap criterion), `FireMonitorCoverage` (wind drift, line-of-sight), `FireWaterCoverageAssessment` (separates coverage / flow / **pressure** deficits), `ActiveFireProtectionScreening` (inventory→PFP→fire-water hierarchy, one-directional substitution rule) |
| `src/main/java/neqsim/process/automation/` | `ProcessAutomation` with typed write validation, `setValuesWithRollback`, and audit-log diagnostics |
| `examples/notebooks/` | Jupyter notebook examples |
| `devtools/new_task.py` | Task-solving script |
| `devtools/neqsim_runner/` | Supervised simulation runner — isolated subprocess per job, auto-retry, checkpoint/resume, SQLite state. Use `AgentBridge` for task-solving integration. |
| `devtools/pdf_to_figures.py` | Convert PDF pages to PNG images for AI analysis. Use `pdf_to_pngs()` for single files, `pdf_folder_to_pngs()` for batch. Requires `pymupdf`. |
| `devtools/skill_search.py` | Semantic skill retrieval — TF-IDF + cosine over every SKILL.md `description`. Run `python devtools/skill_search.py "<task title>" --top 5` at the start of a task to load the right skills. Falls back to Jaccard tokens if scikit-learn is missing. |
| `devtools/validate_task_results.py` | CI gate that mirrors `TaskResultValidator` rules in pure Python. Modes: positional, `--all`, `--changed` (reads `CHANGED_FILES`), `--enterprise-gate`. Warns when `step1_scope_and_research/capability_assessment.md` is missing or unfilled. With `--enterprise-gate` a Standard/Comprehensive task carrying neither a `benchmark_validation` nor a model-vs-plant comparison **fails** instead of warning. Task folders outside the repo are supported (`neqsim --set-task-root`). |
| `devtools/verify_skill_api_refs.py` | API-drift linter: resolves every fully-qualified `neqsim.*` class reference in `.github/skills/` and `.github/agents/` against the Java source tree and fails on unresolved ones; warns on missing or stale `last_verified`. Bare class names are ignored on purpose (false positives). Runs in `skills_agents_lint.yml`. |
| `neqsim report [TASK_DIR]` | Runs the canonical `devtools/task_template/step3_report/generate_report.py` against **any** task folder (old or new), forwarding `--paper` / `--template PATH` / `--no-template`. Use it instead of copying the generator into a task; equivalent to `--task-dir PATH` / `NEQSIM_TASK_DIR`. |
| `neqsim work-record [TASK_DIR]` (`devtools/generate_work_record.py`) | Writes `step3_report/WORK_RECORD.md` — the method-and-provenance companion to the report, **generated automatically by every report run** (opt out with `report.work_record: skip`): every script/notebook with purpose, outputs and re-run command; declared source systems and whether their captured evidence exists; collected reference documents; cached data files; key results and figures; an annotated folder map; and the reproduction sequence. Built from the folder so it cannot drift. Hand-written NARRATIVE blocks are preserved on regeneration; `--check` fails while one still holds template text or an artifact is missing. |
| `.github/workflows/task_quality_gate.yml` | PR gate: runs `validate_task_results.py --changed --enterprise-gate` + `consistency_checker.py` on changed task folders only. |
| `.github/workflows/task_nip_issues.yml` | On push to master, opens one labelled GitHub issue per newly added `neqsim_improvements.md` (deduped by title). |
| `step1_scope_and_research/capability_assessment.md` (per task) | Mandatory artifact for Standard/Comprehensive tasks: capability requirements matrix, NeqSim coverage check, gap implementation plan, skills to load. Auto-scaffolded into every new task by `new_task.py`. |
| `.github/agents/literature-scout.agent.md` | Literature & internal-database scout — pulls papers, standards, and STID/vendor docs into `step1_scope_and_research/references/`, writes `references/manifest.json`, summarises into `notes.md`. |
| `.github/agents/review.agent.md` | Review agent — grades a task folder before PR (schema, consistency, capability_assessment, notebook execution, figure traceability, repo-memory hits). Read-only. |
| `devtools/verify_skills_agents.py` | CI lint for `.github/skills/` and `.github/agents/` — enforces YAML front-matter, validates `skill-index.json` references, flags orphan skills. |
| `devtools/generate_agent_skill_map.py` | Auto-generates `docs/development/AGENT_SKILL_MAP.md` from `Loaded skills:` lines in agent files. CI fails if the map is stale. |
| `.github/workflows/skills_agents_lint.yml` | PR/push gate that runs the verifier and the map generator; ensures skill↔agent linkage stays accurate. |
| `docs/development/TASK_SOLVING_GUIDE.md` | Full workflow guide |
| `docs/development/CODE_PATTERNS.md` | Copy-paste code starters |
| `docs/development/TASK_LOG.md` | Past solved tasks (search before starting) |
| `.github/agents/solve-task.agent.md` | Detailed agent instructions |
| `.github/agents/router.agent.md` | Request routing and multi-agent composition |
| `.github/agents/capability-scout.agent.md` | Capability assessment, gap analysis, implementation planning |
| `.github/agents/field-development.agent.md` | Field development studies, concept selection, economics |
| `.github/agents/engineering-deliverables.agent.md` | Engineering deliverables (PFD, instruments, fire, noise, etc.) |
| `.github/agents/extract-process.agent.md` | Extract process info from documents → NeqSim JSON / ProcessModule → simulation |
| `src/main/java/neqsim/process/fielddevelopment/` | Field development workflows, economics, screening |
| `src/main/java/neqsim/process/util/fielddevelopment/` | Production profiles, scheduling, DCF calculator |
| `docs/fielddevelopment/` | Field development documentation |
| `CHANGELOG_AGENT_NOTES.md` | API changes agents need to know about |
| `src/main/java/neqsim/process/equipment/heatexchanger/heatintegration/` | Pinch analysis (PinchAnalysis, HeatStream) for heat integration |
| `src/main/java/neqsim/process/equipment/powergeneration/` | Power generation (GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem) |
| `src/main/java/neqsim/util/agentic/` | Agentic infrastructure (TaskResultValidator, SimulationQualityGate, AgentSession). `AgentBenchmarkSuite` declares reference problems and `AgentBenchmarkRunner` executes them against NeqSim; `AgentBenchmarkRunnerTest` is the CI accuracy gate (`agent_benchmark` job in `verify_build.yml`) and writes `target/agent_benchmark_summary.txt` for the job summary. Reference values are sourced from CoolProp HEOS via `devtools/source_benchmark_references.py` — regenerate them there rather than editing numbers by hand. Only problems whose reference source is **not** prefixed `UNVERIFIED` are asserted on; the two still unverified are underdetermined (no declared flow rate / vessel volume) and stay visible in the summary. |
| `.github/agents/reaction-engineering.agent.md` | Reaction engineering systems design |
| `.github/agents/control-system.agent.md` | Control system and instrumentation design |
| `.github/agents/emissions-environmental.agent.md` | Emissions calculation and environmental compliance |
| `.github/agents/ccs-hydrogen.agent.md` | CCS value chain and hydrogen systems (CO2 transport, injection, H2 blending) |
| `.github/agents/production-chemistry.agent.md` | Production-chemical selection and dosing, cocktail compatibility, scavenger sizing, demulsifier vs oil-in-water spec, chemical root-cause analysis |
| `.github/agents/technical-reader.agent.md` | Read technical documents (PDF, Word, Excel) and engineering images (P&IDs, mechanical drawings, vendor datasheets, performance maps, phase envelopes) — extract equipment data, compositions, requirements, stream tables, piping topology, dimensions, and operating conditions |

### Skills Reference

Skills are reusable knowledge packages loaded automatically by agents:

| Skill | Purpose |
| --- | --- |
| `neqsim-api-patterns` | EOS selection, fluid creation, flash, equipment patterns |
| `neqsim-thermodynamic-initialization` | Choosing and auditing `init(...)` / `initProperties()` levels — lowest correct level for the properties actually consumed, and performance review of initialization calls |
| `neqsim-process-modeling` | ProcessSystem flowsheet construction — streams, separators, compressors, heat exchangers, valves, pumps, columns, recycles, adjusters, topology, result extraction, and validation for executable process simulations |
| `neqsim-java8-rules` | Forbidden Java 9+ features, replacement patterns |
| `neqsim-notebook-patterns` | Jupyter notebook structure, visualization, performance estimation |
| `neqsim-optimization-and-doe` | Process flowsheet optimization & DoE — decision tree across the 30 NeqSim optimizer classes (ProcessOptimizationEngine, ProductionOptimizer, SQPoptimizer, MultiObjectiveOptimizer, MonteCarloSimulator, BatchStudy, ProcessSimulationEvaluator, DesignOptimizer), SciPy/Pyomo/BoTorch bridging, sensitivity, Pareto, uncertainty |
| `neqsim-pdf-ocr` | OCR text extraction from scanned PDFs and P&IDs — OCRmyPDF + Tesseract + pytesseract, tag-pattern post-filtering, P&ID-tuned settings (400 DPI, sparse PSM) |
| `neqsim-troubleshooting` | Recovery strategies for convergence failures, zero values, phase issues |
| `neqsim-input-validation` | Pre-simulation checks (T, P, composition, component names) |
| `neqsim-regression-baselines` | Baseline management for preventing accuracy drift |
| `neqsim-standards-lookup` | Industry standards lookup — equipment-to-standards mapping, CSV database queries, compliance tracking in results.json |
| `neqsim-agent-handoff` | Structured schemas for multi-agent result passing (includes lifecycle state handoff) |
| `neqsim-physics-explanations` | Plain-language explanations of engineering phenomena |
| `neqsim-capability-map` | Structured inventory of NeqSim capabilities by discipline |
| `neqsim-model-calibration-and-data-reconciliation` | Digital twin model calibration and data reconciliation — bounded parameter tuning, steady-state windowing, residual diagnostics, train/validation reporting |
| `neqsim-field-development` | Field development workflows, concept selection, lifecycle management |
| `neqsim-field-economics` | NPV, IRR, cash flow, tax regimes (Norwegian NCS, UK), cost estimation |
| `neqsim-subsea-and-wells` | Subsea systems, well design, SURF cost, tieback analysis |
| `neqsim-production-optimization` | Decline curves, bottleneck analysis, gas lift, network optimization |
| `neqsim-process-extraction` | Extract process data from text/tables/PFDs into NeqSim JSON builder format |
| `neqsim-unisim-reader` | UniSim COM reader — component/EOS/operation-handler registry mapping, topology reconstruction, forward refs, verification. **Default E300 fluid export** for lossless transfer of critical properties (Tc, Pc, omega, MW, BIPs) including hypothetical/pseudo components. Uses `UniSimOperationHandler` strategies (`native`, `adapter`, `reference`, `control`, `column_internal`, `skip`) and `_unisim_operation_mapping` JSON summaries; balance/virtual/template placeholders use `UnisimCalculator` adapters. Includes TUTOR1 verified reference case, DistillationColumn solver limitations for NGL-rich feeds, HeatExchanger UA tuning notes, separator 2-phase/3-phase auto-detection (flashtank with WaterProduct promoted to ThreePhaseSeparator), orientation detection (vertical → GasScrubber, horizontal → Separator), and entrainment extraction (liquid carryover, gas carry-under, water-in-oil, oil-in-water). |
| `neqsim-eos-regression` | EOS parameter regression — kij tuning, PVT matching (CME, CVD), C7+ characterization, scipy optimization |
| `neqsim-reaction-engineering` | Reactor patterns — GibbsReactor, PlugFlowReactor, StirredTankReactor, KineticReaction, CatalystBed, **AnaerobicDigester, FermentationReactor, BiogasUpgrader, biorefinery modules** |
| `neqsim-dynamic-simulation` | Dynamic simulation — runTransient, PID controllers, transmitters, tuning, depressurization |
| `neqsim-distillation-design` | Distillation column design — solver selection, feed tray rules, convergence, internals sizing |
| `neqsim-electrolyte-systems` | Electrolyte/brine chemistry — SystemElectrolyteCPAstatoil, ions, scale risk, MEG injection |
| `neqsim-production-chemistry` | Production chemistry — `ProductionChemical` inventory, `ChemicalCompatibilityAssessor` cocktail check, per-threat dose-response (scale/corrosion/THI/KHI/wax/asphaltene inhibitors, H2S scavenger), `InhibitorInjectionPoint` in a flowsheet, demulsifier dose vs oil-in-water spec, `ProductionChemicalScaleScenario` treatment effect on brine, explainable chemical `RootCauseAnalyser`, `ChemistryUncertaintyAnalyzer` |
| `neqsim-flow-assurance` | Flow assurance — hydrate, wax, asphaltene, CO2/H2S corrosion (NORSOK M-506 + electrolyte pH + FeCO3 film, de Waard-Milliams), mineral scale (SI, kinetics, brine mixing), per-segment corrosion+scale profiles, pipeline hydraulics, inhibitor dosing |
| `neqsim-flow-accelerated-corrosion` | Flow-accelerated corrosion in closed heating/cooling-medium, boiler-feedwater and WHRU loops — `FlowAcceleratedCorrosion` (mass-transfer index, 150 °C solubility peak, bend/weld geometry, Cr upgrade) and `AmineBufferedPH` (laboratory pH to in-situ pH at temperature, alkaline margin above neutrality). Distinct from NORSOK M-506 CO2 corrosion and from erosion-corrosion |
| `neqsim-water-hammer` | Water/liquid hammer screening — `WaterHammerPipe`, `WaterHammerStudy`, MCP `runWaterHammer`, STID route geometry, tagreader event windows, valve closure, pump trip, pressure envelopes |
| `neqsim-ccs-hydrogen` | CCS and hydrogen — CO2 phase behavior with impurities, dense phase transport, injection wells, H2 blending |
| `neqsim-power-generation` | Power generation — gas turbines, steam turbines, HRSG, combined cycle, heat integration |
| `neqsim-platform-modeling` | Production platform process modeling — multi-stage separation, recompression with compressor curves and anti-surge, export/injection compression, scrubber liquid recycles, Cv valve flow, iteration strategies, structured result extraction. Derived from 15+ NCS platform models |
| `neqsim-technical-document-reading` | Read technical documents and engineering images — PDF/Word/Excel extraction, P&ID topology, vendor datasheet parsing, image analysis with view_image, performance map digitization, figure discussion generation |
| `neqsim-trapped-liquid-fire-rupture` | Blocked-in liquid fire rupture workflow — evidence retrieval, trapped inventory, API 521 fire exposure, material/flange derating, PFP demand, and source-term handoff |
| `neqsim-stid-retriever` | Retrieve engineering documents (compressor curves, mechanical drawings, data sheets) for tasks. Supports local dirs, manual upload, pluggable retrieval backends (configured via gitignored `devtools/doc_retrieval_config.yaml`). Includes relevance filtering by task type and retrieval manifests for traceability |
| `neqsim-process-safety` | HAZOP guidewords, LOPA worksheets via `LOPAResult`, SIL determination via `SafetyInstrumentedFunction` (IEC 61508/61511), bow-tie via `BowTieModel`/`BowTieAnalyzer`, 5×5 risk matrix via `RiskMatrix` (NORSOK Z-013, CCPS, API 754) |
| `neqsim-self-heating-ignition` | Spontaneous ignition of combustible liquid absorbed into porous insulation (lagging fires) — Frank-Kamenetskii & Semenov criticality, critical thickness/temperature, induction time, Arrhenius fitting from EN 15188 / ASTM E2021 basket tests. Anchors on `neqsim.process.safety.selfheating`. Note `RunawayReactionAnalyzer` is lumped-adiabatic and cannot answer this |
| `neqsim-firewater-deluge-design` | Fire-water / deluge coverage — NORSOK S-001 / ISO 13702 / NFPA 15 application rates, area vs dedicated object protection, nozzle-net sizing from **both** the flow and the spray-overlap criterion, fire-monitor screening with wind drift, hydraulic-feasibility gating against an existing fire-water system (coverage / flow / **pressure margin**), and the one-directional active-vs-passive substitution rule. Anchors on `neqsim.process.safety.firewater` |
| `neqsim-heat-integration` | Pinch analysis with `PinchAnalysis` — composite & grand composite curves, ΔTmin selection, MER targeting, retrofit diagnostics, auto-extract via `PinchAnalysis.fromProcessSystem(process, dTmin)` |
| `neqsim-equipment-cost-estimation` | Equipment-level CAPEX via `CostEstimationCalculator` — Turton/Peters/Ulrich correlations, CEPCI escalation (2019→2025), material/pressure factors, AACE class 1–5, Cp→Cbm→Ctm→Cgr stackup |
| `neqsim-relief-flare-network` | PSV sizing per API 520 (gas/liquid/two-phase) via `ReliefValveSizing`, API 521 fire heat input, flare radiation API 537 via `Flare.estimateRadiationHeatFlux`, header back-pressure & Mach checks |
| `neqsim-controllability-operability` | Operating envelope mapping, turndown analysis, control valve sizing per ISA-75/IEC 60534 via `ThrottlingValve`, startup/shutdown sequences, recycle stability diagnostics |
| `neqsim-utilities-specification` | Steam levels (HP/MP/LP), cooling water (ΔT 10–15 °C), instrument air (≤ −40 °C dew point), fuel gas (Wobbe Index), N₂, demin water, refrigeration; per NORSOK U-001, ISA-7.0.01 |
| `neqsim-professional-reporting` | Deliverable quality — `results.json` master schema, figure→discussion→linked_results traceability, KaTeX math, citations, AACE class declaration, uncertainty disclosure (P10/P50/P90), risk register, benchmark validation |

### API Verification (Mandatory)

Before using any NeqSim class in examples or notebooks:
1. Search for the class to confirm it exists
2. Read constructor and method signatures
3. Use only methods that actually exist with correct parameter types
4. Do NOT assume convenience overloads — check first

### Documentation Code Verification (Mandatory)

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

### Notebook Execution Verification (Mandatory)

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

### Documentation

All classes and methods need complete JavaDoc (class description, `@param`,
`@return`, `@throws`). HTML5-compatible: use `<caption>` in tables, no
`summary` attribute, no `@see` with plain text, and note that `<ul>`/`<ol>`
implicitly close the current paragraph so a trailing `</p>` after a list breaks
JavaDoc HTML. Run `./mvnw javadoc:javadoc`
to verify.


## Part B - Coding and documentation rules (formerly the full .github/copilot-instructions.md)

﻿# NeqSim AI Guidance for Coding Agents

### Quick Orientation

> **Start here:** Read `CONTEXT.md` in the repo root for a 60-second overview of the
> entire codebase - repo map, code patterns, build commands, and constraints.
>
> **Solving a task?** See `docs/development/TASK_SOLVING_GUIDE.md` for the step-by-step
> workflow: classify the task, find similar past solutions, write code, verify, log it.
>
> **Looking for code patterns?** `docs/development/CODE_PATTERNS.md` has copy-paste
> starters for every common task (fluids, flash, equipment, PVT, tests, notebooks).
>
> **Was this solved before?** Search `docs/development/TASK_LOG.md` for keywords.
> Every solved task gets an entry there - check before starting from scratch.

---

### WARNING: CRITICAL: Docker Required for Linux-Only Tools on Windows

Some skills wrap external engines with **no native Windows build** — they are
Linux software: **FluidMagic** (fluid characterization engine,
`enterprise-fluidmagic-characterization`), **OpenFOAM** (CFD,
`neqsim-cfd-coupling`), **OPM Flow** (reservoir simulation,
`neqsim-near-well-and-injectivity`), and any other skill/agent that shells out
to a Linux-only binary. On a Windows machine, **Docker Desktop (or WSL2) must
be installed** before one of these is actually run — the request/case/hand-off
can still be built and written without it, but nothing executes locally until a
Linux runtime is available. Report a missing Docker/WSL2 environment as a
blocker rather than silently skipping the calculation.

### WARNING: CRITICAL: Java 8 Compatibility (READ FIRST)

**All code MUST compile with Java 8.** The CI build will FAIL if you use Java 9+ features.

**This applies to ALL Java files including test classes in `src/test/java/`.**

#### FORBIDDEN Java 9+ Features (NEVER USE):
| Forbidden | Java 8 Alternative |
|---|---|
| `"str".repeat(n)` | `StringUtils.repeat("str", n)` (Apache Commons) |
| `var x = ...` | Explicit type declaration: `String x = ...`, `Map<String, Object> map = ...` |
| `List.of(a, b)` | `Arrays.asList(a, b)` or `Collections.singletonList(a)` |
| `Set.of(a, b)` | `new HashSet<>(Arrays.asList(a, b))` |
| `Map.of(k, v)` | `Collections.singletonMap(k, v)` or HashMap |
| `str.isBlank()` | `str.trim().isEmpty()` |
| `str.strip()` | `str.trim()` |
| `str.lines()` | `str.split("\\R")` or BufferedReader |
| `Optional.isEmpty()` | `!optional.isPresent()` |
| Text blocks `"""..."""` | Regular strings with `\n` |
| Records | Regular class with fields |
| Pattern matching `instanceof` | Traditional instanceof + cast |

#### Common `var` Replacement Examples:
```java
// WRONG (Java 10+):
var map = someMethod.toMap();
var list = getItems();
var result = calculate();

// CORRECT (Java 8):
Map<String, Object> map = someMethod.toMap();
List<String> list = getItems();
CalculationResult result = calculate();
```

#### Required Import for String Repeat:
```java
import org.apache.commons.lang3.StringUtils;
// Usage: StringUtils.repeat("=", 70)
```

---

### ⚠️ CRITICAL: Run Spotless After Editing ANY Java File (READ SECOND)

**The CI runs `spotless:check` (via the pre-commit GitHub Action) and FAILS the
build on any unformatted `.java` file.** AI-generated/edited Java is NOT
auto-formatted — agents that hand-indent multi-line method chains or long string
concatenations WILL produce violations (this is exactly what failed PR #2324).

**Mandatory workflow after creating OR editing ANY `.java` file (main, test, or
examples) — before committing:**

```powershell
.\mvnw.cmd spotless:apply    # reformats to the project style (Windows)
git add <the reformatted files>
.\mvnw.cmd spotless:check    # OPTIONAL local verify — this is what CI runs
```

- Run `spotless:apply` (NOT just `check`) to actually fix the files.
- Do NOT rely on local pre-commit hooks being installed — run it explicitly.
- NEVER bypass with `git commit --no-verify`.
- This is non-negotiable: a single unformatted file fails the entire CI build.

---

### Quick Commands

- **Package and Update Python**: When the user says "package and update python" or similar, run these commands:
  ```powershell
  .\mvnw.cmd package -DskipTests
   Copy-Item -Path "C:\Users\ESOL\Documents\GitHub\neqsim\target\neqsim-3.22.0.jar" -Destination "C:\Users\ESOL\AppData\Roaming\Python\Python312\site-packages\neqsim\lib\" -Force
  ```
  This builds the NeqSim JAR and copies it to the Python neqsim package for immediate use. Note: the runtime loads `lib/*` (flat), so copy the JAR directly into `neqsim\lib\`, not a `java11`/`java8` subfolder.

---

### API Consistency (MANDATORY)

When creating example files or documentation that references existing classes:

1. **ALWAYS verify method signatures** before using them - read the actual class to confirm:
   - Constructor parameters (type and order)
   - Method names exist and have correct parameter types
   - Return types match expected usage

2. **Common API verification pattern**:
   ```
   # Before writing example code that uses SomeClass:
   1. Search for the class: file_search("**/SomeClass.java")
   2. Read constructor and method signatures
   3. Use only methods that actually exist with correct parameter types
   ```

3. **Do NOT assume API patterns** - different classes may have different conventions:
   - Some constructors take `String name`, others take `ProcessSystem`
   - Method names like `addEquipment` vs `addEquipmentReliability` vs `addEquipmentMtbf`
   - Parameter counts vary (e.g., 3 params vs 4 params)

4. **Inner classes and enums** - verify the exact location:
   - Enums may be in different classes: `RiskEvent.ConsequenceCategory` vs `RiskMatrix.ConsequenceCategory`
   - Inner classes require full path: `BowTieModel.Threat`, `PortfolioRiskAnalyzer.CommonCauseScenario`
   - Check imports in the actual class to see which enum/type it uses

5. **Object-based vs convenience APIs** - do NOT assume convenience methods exist:
   - Wrong: `model.addThreat("name", 0.1)` (assuming convenience overload)
   - Right: First check if method takes objects: `model.addThreat(new Threat(...))`
   - Many APIs use builder patterns or require creating objects explicitly

6. **Common API mistakes to avoid**:
   - Assuming `getXxx95()` exists when actual method is `getXxx(int percentile)`
   - Assuming enum constants like `SEVERE_WEATHER` when actual is `CommonCauseType.WEATHER`
   - Assuming 1-arg constructors when 2+ args are required
   - Calling methods on wrong class (e.g., `analyzer.getFrequency()` vs `model.getFrequency()`)
   - Assuming `calculate()` when actual method is `calculateRisk()` or `run()`
   - Assuming convenience overloads like `addAsset(name, value1, value2, value3)` when API is `addAsset(id, name, value)`
   - Using descriptive names as IDs when API distinguishes between `id` and `name` parameters

### Documentation Code Verification (MANDATORY)

**Every code example in documentation MUST be verified by a runnable test.**
**Documentation is NOT complete until the test has been executed and passes.**

When writing documentation that includes Java or Python code examples:

1. **Write a JUnit 5 test** that exercises every API call shown in the documentation.
   - Append to `src/test/java/neqsim/DocExamplesCompilationTest.java` for general utilities.
   - Or create a dedicated test in the appropriate package directory.
   - The test must instantiate classes, call all documented methods, and assert results are non-null/valid.

2. **Run the test** and confirm all assertions pass before finalizing documentation.
   - Use `./mvnw test -Dtest=DocExamplesCompilationTest` (or the specific test class).
   - **If the test fails, fix the documentation code - do NOT finalize with broken examples.**
   - This step is NON-NEGOTIABLE - never skip it, even for "simple" examples.

3. **Keep tests in sync** - when documentation changes, update the corresponding test.

4. **For Python examples**: verify the equivalent Java API calls work (Python examples call
   the same Java methods via jpype). If the Java test passes, the Python example will work.

5. **Common doc-code bugs to catch with tests**:
   - Plus fraction names with `+` character (`"C20+"` crashes - use `"C20"`)
   - Wrong method names (`getUnitOperation()` vs `getUnit()`)
   - Wrong parameter types (`int` vs `double`)
   - Calling characterization before setting mixing rule
   - Wrong risk threshold descriptions not matching source logic
   - Methods requiring unit strings (e.g., `setDesignAmbientTemperature(15.0, "C")` not `setDesignAmbientTemperature(15.0)`)
   - Getter methods requiring arguments (e.g., `getFanStaticPressure(flow)` not `getFanStaticPressure()`)

---

- **Mission Focus**: NeqSim is a Java toolkit for thermodynamics and process simulation; changes usually affect physical property models (`src/main/java/neqsim/thermo`) or process equipment (`src/main/java/neqsim/process`).
- **MCP-First Calculation Policy (MANDATORY when an MCP server is available)**: For any single NeqSim calculation (flash, PVT, process, standards, sizing, ...), check first whether a curated `mcp_neqsim_*` tool covers it and use it. If no curated tool matches, try the generic `runCapability` route (`search` → `inspectApi` → invoke) before writing code. Only fall back to the Python API (`import neqsim`) or Java in a checkout when MCP cannot do the job (inspect-only route, needs loops/plotting/state/notebooks/reports) or the task lives inside `/solve-task`. If NeqSim itself lacks the capability, implement it in Java (+ tests) rather than working around the gap in Python. See `neqsim-api-patterns` skill § "MCP server vs. Python/Java API".
- **Architecture Overview**: Packages map to the seven base modules in docs/modules.md; keep new code within the existing package boundaries so thermodynamic, property, and process layers stay decoupled.
- **Property Initialization After Flash (CRITICAL)**: After any flash calculation (`TPflash`, `PHflash`, `PSflash`, etc.), you MUST call `fluid.initProperties()` before reading physical/transport properties. `init(3)` alone does NOT initialize transport properties (viscosity, thermal conductivity). Use `fluid.initProperties()` which calls both `init(2)` + `initPhysicalProperties()`. Without this, `getViscosity()`, `getThermalConductivity()`, and `getDensity()` may return **zero**.
- **Phase Envelope Branch Labels (CRITICAL)**: When using `calcPTphaseEnvelope(true, 1.0)` (bubblePointFirst=true), `getBubblePointTemperatures()` returns physically DEW curve data and `getDewPointTemperatures()` returns physically BUBBLE curve data (labels are swapped). **Always classify branches by physical reasoning**: the branch with the higher maximum temperature is the dew curve (contains cricondentherm). See `neqsim-api-patterns` skill for the correct pattern.
- **Thermo Systems**: Fluids are represented by `SystemInterface` implementations such as `SystemSrkEos` or `SystemSrkCPAstatoil`; always set a mixing rule (`setMixingRule("classic")` or numeric CPA rule) and call `createDatabase(true)` when introducing new components.
- **Process Equipment Pattern**: Equipment extends `ProcessEquipmentBaseClass` and is registered inside a `ProcessSystem`; `ProcessSystem` enforces unique names and handles recycle/adjuster coordination, so reuse it for multi-unit workflows. Use `MultiPortEquipment` as the base class for equipment with multiple inlet/outlet streams.
- **Stream Introspection**: Every `ProcessEquipmentInterface` exposes `getInletStreams()` and `getOutletStreams()` returning `List<StreamInterface>`. Use these to walk flowsheets programmatically, build topology graphs, or auto-generate DEXPI/P&IDs. Equipment classes (Separator, Mixer, Splitter, etc.) override these to return their specific connected streams.
- **Named Controllers**: Attach multiple controllers to equipment via `addController("tag", controller)`, retrieve with `getController("tag")`, list all with `getControllers()`. The legacy `setController()`/`getController()` still work (backward-compatible). During dynamic simulation `runTransient()`, the `ProcessSystem` explicitly runs all controller devices and measurement devices each timestep.
- **Explicit Connections**: Record typed connection metadata via `process.connect(source, target, ProcessConnection.ConnectionType.MATERIAL, "label")`. Connection types: `MATERIAL`, `ENERGY`, `SIGNAL`. Query with `process.getConnections()`.
- **Unified Element Model**: `ProcessElementInterface` is the common supertype for equipment, controllers, and measurement devices. Query all elements with `process.getAllElements()`. This enables DEXPI export, topology analysis, and flowsheet introspection.
- **Streams & Cloning**: Instantiate feeds with `Stream`/`StreamInterface`, call `setFlowRate`, `setTemperature`, `setPressure`, then `run()`; clone fluids (`system.clone()`) before branching to avoid shared state between trays or unit operations.
- **Distillation Column**: `DistillationColumn` provides sequential, damped, and inside-out solvers; maintain solver metrics (`lastIterationCount`, `lastMassResidual`, `lastEnergyResidual`) and feed-tray bookkeeping when altering column logic to keep tests like `insideOutSolverMatchesStandardOnDeethanizerCase` green.
- **ProcessSystem Utilities**: Use `ProcessSystem.add(unit)` to build flowsheets, `run()`/`run(UUID)` for execution, `copy()` when duplicating equipment, `connect()` for explicit connections, and `getAllElements()` to query all equipment, controllers, and measurements; modules can self-initialize through `ModuleInterface` - respect these hooks if you add packaged subsystems.
- **ProcessModel for Multi-Area Plants (MANDATORY)**: For large plants (platforms, gas plants), split into separate `ProcessSystem` objects per process area then combine with `ProcessModel`. Use `plant.add("area name", processSystem)` to register named areas, `plant.run()` iterates until convergence, `plant.get("area name")` retrieves sub-processes, and `plant.getConvergenceSummary()` reports status. See the reference platform models for the canonical pattern: each area is a Python function returning a `ProcessSystem`, cross-system streams are shared by object reference, and all systems are composed into a `ProcessModel` at the end. **NEVER** add a `ProcessModule` or `ProcessModel` to a `ProcessSystem` - it will throw TypeError.
- **Automatic recycle insertion (PREFERRED over hand-written tears)**: A loop wired straight back into an upstream mixer, with no `Recycle` in it, has no tolerance, acceleration or convergence report of its own; across `ProcessModel` areas it is closed only by the outer Gauss-Seidel pass. Call `process.makeRecycles()` / `plant.makeRecycles()` (optionally `makeRecycles(tolerance)`, default `1e-2`) to find those loops and close them with seeded, tuned `Recycle` units, or `setAutoRecycles(true)` to have `run()` and every `runUntilConverged(...)` overload do it. The tear point is the inlet with the smallest recycle ratio (tear flow / total inlet flow of the consuming unit), one edge is torn per round, and each generated recycle gets `setAdaptiveAcceleration(true)` plus an absolute flow tolerance at 1e-6 of the area's largest flow. Self-seeding and idempotent; only `Mixer` / `Manifold` inlets are tearable (others are logged and left alone); `setAutoRecycles` defaults to **false**. Backed by `neqsim.process.processmodel.AutoRecycleBuilder`; see `docs/process/controllers.md#automatic-recycle-insertion`.
- **Self-configuring convergence (do NOT hand-pick numbers)**: `plant.runUntilConverged(maxIterations)` derives its own flow-noise filters (boundary flow floor, absolute flow tolerance, per-unit low-flow bypass) from the plant's own feed rate, and — when no tolerance was set — its own accuracy: `DEFAULT_ENGINEERING_TOLERANCE` (1e-3 relative on flow/T/P) instead of the historical 1e-4, plus acceptance of a residual that stops improving over `AUTO_TOLERANCE_STALL_WINDOW` (5) outer passes while below `getAutoToleranceCeiling()` (1e-2). Report with `getAutoTuningSummary()` / `getAutoToleranceSummary()` (also in `getConvergenceSummary()` and the `autoTuning` / `autoTolerance` blocks of `getConvergenceReportJson()`). Any explicit `setTolerance()` / per-variable setter / `runUntilConverged(n, tol)` marks the tolerance user-owned and disables **both** behaviours — so do not set `1e-3` "to be helpful". Opt out with `setAutoTolerance(false)` / `setAutoConvergenceTuning(false)`.
- **Automation API (PREFERRED for agents)**: Use `ProcessAutomation` for string-addressable variable access instead of navigating Java class hierarchies. Get the facade via `process.getAutomation()` or `plant.getAutomation()` — **the same cached instance is returned on every call** so diagnostics history, learned corrections, and the dirty flag persist across agent turns. Discover equipment with `getUnitList()`, list variables with `getVariableList("unitName")` (returns `SimulationVariable` with INPUT/OUTPUT type, address, unit, description), read values with `getVariableValue("Unit.stream.property", "unit")`, write with `setVariableValue("Unit.property", value, "unit")`. For multi-area models, use area-qualified addresses: `"Area::Unit.stream.property"` with `getAreaList()` for discovery.
- **Agentic Automation Extensions**: `ProcessAutomation` now exposes batch and introspection methods that emit a stable JSON schema (`SCHEMA_VERSION = "1.0"`):
  - **Batch I/O**: `getValues(addresses, unit)` returns `Map<String, Double>` of successfully read values; `setValues(updates, unit, runAfter)` writes many inputs and optionally runs once.
  - **Dirty tracking**: `isDirty()`, `runIfDirty()`, and `setVariableValueAndRun(address, value, unit)` avoid redundant `run()` calls — the dirty flag flips on every successful write and clears after `run()`.
  - **Introspection**: `describe()` returns the full unit/variable manifest as JSON; `snapshot(scope)` dumps variable values for a unit, area, or `"*"`; `getTopology()` lists equipment and `ProcessConnection` edges; `getNeighbors(unit)` returns immediate upstream/downstream units.
  - **Structured reads**: `getStructured(address)` returns a `JsonElement` — composition addresses (`...composition`, `...components`, `...phaseFractions`, `...kvalues`) yield objects/arrays instead of crashing the scalar accessor.
  - **Pre-flight validation**: `validateAddress(address)` returns `null` for good addresses or a `DiagnosticResult` with the proper `ErrorCategory` (no exception thrown). `getAllowedUnits(address)` lists valid UOM strings.
  - **Diagnostic taxonomy**: `setVariableValueSafe`/`getVariableValueSafe` JSON responses include category-tagged errors for `UNIT_NOT_FOUND`, `PROPERTY_NOT_FOUND`, `PORT_NOT_FOUND`, `READ_ONLY_VARIABLE`, `VALUE_OUT_OF_BOUNDS`, `UNKNOWN_UNIT`, `INVALID_ADDRESS_FORMAT`, and `CONVERGENCE_FAILURE`.
  - **Thread safety**: `AutomationDiagnostics` uses a `Collections.synchronizedList` history and a `ConcurrentHashMap` of learned corrections so multiple agents may share a facade.
- **Closed-Loop Optimization — `evaluate()` (PREFERRED for agent loops)**: `run()` returns `void`, so the agentic run primitives are the key addition that makes a flowsheet an optimization target — each returns **one schema-versioned JSON object and never throws**. Use `evaluate(setpoints, setpointUnit, readbacks, readbackUnit, maxIterations, tolerance)` (and the convenience overload `evaluate(setpoints, unit, readbacks)`) as the atomic optimizer step: it applies a batch of setpoints, runs to convergence, gates feasibility, and reads back objectives in one call. Gate trials on the single **`feasible`** flag (true only when the run did not throw, the model converged, no unit failed, and every setpoint was accepted). Rejected setpoints land in `setpointsRejected` and bad read-backs in `readbackErrors` — both without throwing, so a malformed candidate degrades one trial instead of crashing the loop. Pass `null` as the unit for a mixed-unit batch (each variable uses its default unit: bara, K, kg/hr). Lower-level gated runs: `runUntilConvergedJson(maxIter, tol)` (multi-area embeds the nested `convergence` report + per-area `areas`), `runJson()` (single run with structured outcome), `getRunStatusJson()` (last status without re-running). All clear the dirty flag even on failure. Default tolerance `5e-3` is robust for plants with near-zero-flow anti-surge recycles. Through jpype wrap as `json.loads(str(result))`. Pair with `getAdjustableParameters()` for the bounded decision space.
- **Capacity Observation Snapshot — `getUtilizationSnapshot()` (the observation vector)**: `evaluate()` is the action+reward step; `getUtilizationSnapshot()` is the matching observation step. Both `ProcessSystem` and `ProcessModel` expose `getUtilizationSnapshotJson()`, and `ProcessAutomation.getUtilizationSnapshot()` delegates to whichever it wraps. The snapshot is **side-effect-free** (never calls `run()`, only reads already-computed `CapacityConstraint` utilization) so it is cheap to call every step. Per unit it reports `name`, `type`, `maxUtilization` (0–1, NaN→0), `maxUtilizationPercent`, `limitingConstraint`, `feasible`, `hardLimitExceeded`, `power_kW` (compressors/pumps), and a `constraints[]` breakdown; for a `ProcessModel` each unit also carries its `area`. Plant-wide it gives `bottleneck` (highest-utilization unit or `null`), `anyOverloaded`, `anyHardLimitExceeded`, schema `"1.0"`. **Closed-loop RL pattern:** observation = `getUtilizationSnapshot()`, action = `evaluate()` setpoints, reward = an `evaluate()` read-back penalized when `anyOverloaded` or any `maxUtilization > 1`. **Chartless compressors:** surge/stonewall/speed constraints are present-but-disabled (their distance-to-surge is undefined and would otherwise pin utilization at a degenerate flat 100%), so such machines report smooth power-driven utilization — give the `power` constraint a basis via `comp.getMechanicalDesign().setMaxDesignPower(kW)`. **Expanders:** `Expander` overrides the inherited `Compressor` capacity logic so it no longer reports a spurious ~150% — the consumed-power constraints (`power`, `ratedPower`) are removed, `isSimulationValid()` is expander-correct (negative shaft power / cooler outlet are valid), and `expander.setRatedRecoveredPower(kW)` adds a `recoveredPower` HARD constraint. **Provenance:** each constraint in the snapshot now carries its `dataSource` (e.g. `"equipment"`, `"design"`) so an agent can tell a rated limit from an estimate.
- **AgenticProcessOptimizer (ML/agentic optimization)**: `auto.newOptimizer()` returns an `AgenticProcessOptimizer` — a ready-made closed-loop search built on `evaluate()` and designed for ML/agentic loops. It works in **string addresses**, a **never-throwing schema-versioned JSON contract**, and a **replayable trajectory**, so an agent can build a problem straight from `getAdjustableParametersJson()`. Algorithm: bounded Nelder–Mead simplex with deterministic (seeded) random init (same seed + same problem ⇒ identical trajectory). Decision space: `addVariable(addr, lo, hi, unit)` or `useAdjustableParameters()`. Objective: `minimize`/`maximize`/`setObjective(addr, Sense, unit)` or `setObjectiveFunction(Function<Map<String,Double>,Double>)` for custom reward shaping over decisions+constraint readbacks+watches (`addWatch`). Constraints: `addConstraintLessOrEqual`/`addConstraintGreaterOrEqual`/`addConstraint(addr, type, limit, unit, penaltyWeight)` folded in as weighted quadratic penalties. Each trial sets the decision variables, runs one gated `evaluate()`, then reads the objective/constraints — a malformed candidate degrades **one** trial, and `optimize()`/`optimizeToJson()` never throw. Every point is logged as a `Trial` (setpoints, readbacks, objective, penalty, feasibility, score) — the (state, action, reward) tape for offline RL. Call `getReadinessJson()` for a machine-readable self-rating (never_throws/deterministic/bounded_action_space/json_io/reward_shaping/constraint_handling/trajectory_logging/feasibility_gating = full; gradient_based = none; global_optimum_guarantee = partial; parallel_evaluation = none). Tuning: `setMaxEvaluations`, `setInnerConvergence(maxIter, tol)`, `setConvergenceTolerance`, `setSeed`. Distinct from the classic `neqsim.process.util.optimizer` classes (which take a `Function<double[],Double>` over an opaque `ProcessSystem`).
- **Capacity / throughput / quality / batch helpers on `ProcessAutomation` (both `ProcessSystem` and `ProcessModel`)**: string-addressable, never-throwing, schema-versioned JSON helpers that close the loop for maximise-production studies. **`enableCapacityConstraints()`** enables capacity constraints on **every** `CapacityConstrainedEquipment` (separators, pumps, valves, pipelines, heaters/coolers, heat exchangers, manifolds) so any type can bind as the bottleneck — it recreates compressor constraints via `reinitializeCapacityConstraints()` (surge/speed stay disabled when chartless, power stays enabled) rather than the blind `enableAllConstraints()`, and adds the separator Souders-Brown gas-load constraint; set each type's design basis first. **`findMaxThroughputJson(feedAddresses, min, max, unit, utilizationLimit)`** enables the constraints then bisects the *total* feed rate (feeds scaled proportionally) until the first unit reaches `utilizationLimit`, leaving the model at the feasible max and returning `{maxRate, feasibleAtMin, bindingUnit, bindingConstraint, bindingUtilizationPercent}`. **`getProductQualityJson(address[, refTempC])`** returns export-oil RVP/TVP (`Standard_ASTM_D6377`) and gas `cricondenbar_bara`/`cricondentherm_K` (`calcPTphaseEnvelope`) on a cloned fluid (never throws; `rvpError`/`envelopeError` on failure) — the spec side of a maximise-throughput-subject-to-RVP/cricondenbar search. **Routing / feed-scale decision variables**: feed `flowRate` is a writable INPUT; splitters expose one bounded `splitFactor_i` (0–1) INPUT per outlet in `getAdjustableParameters()` (read = current fraction; write = branch weight, renormalised to sum 1) so `AgenticProcessOptimizer.useAdjustableParameters()` picks them up automatically. **`evaluateBatchJson(candidates, unit, readbacks, maxParallel)`** scores a list of setpoint maps in one call — for a `ProcessSystem` with `maxParallel>1` each candidate runs on an independent `ProcessSystem.copy()` on its own thread (genuinely parallel, **live model untouched**), for a `ProcessModel` (no `copy()`) or `maxParallel==1` it runs sequentially; each result carries the full `evaluate` payload (incl. `converged`/`iterations`/`maxError`/`failedUnitName`/`failedUnitError`) + `index`, root reports `parallel`/`feasibleCount`/`firstFeasibleIndex`. **Production + emissions**: compose decision space (bounded setpoints + splitter routing + bounded feed scale) + feasibility (`enableCapacityConstraints` + snapshot) + a reward `production − λ·Σ(compressor power)` (compression power = CO2 proxy) via `setObjectiveFunction`, or `ProductionOptimizer.optimizePareto` `[MAX production, MIN Σ power]`.
- **Self-Healing Automation (PREFERRED for agents)**: Use `getVariableValueSafe()` and `setVariableValueSafe()` instead of direct get/set. These return JSON with the value on success, or diagnostics with suggestions, auto-corrections, and remediation hints on failure. Access `auto.getDiagnostics()` for fuzzy name matching (`autoCorrectName()`), physical bounds validation (`validatePhysicalBounds()`), and operation tracking (`getLearningReport()`). The `AutomationDiagnostics` class learns from past failures - corrections are cached and reused automatically.
- **Lifecycle State (Save/Restore/Compare)**: Use `ProcessSystemState.fromProcessSystem(process)` and `ProcessModelState.fromProcessModel(plant)` to create portable JSON snapshots. Save with `state.saveToFile("model.json")`, load with `ProcessSystemState.loadFromFile("model.json")`, validate with `state.validate()`. Compare versions with `ProcessModelState.compare(v1, v2)` returning a `ModelDiff` (modified parameters, added/removed equipment). Use `toCompressedBytes()`/`fromCompressedBytes()` for network transfer. All state classes live in `neqsim.process.processmodel.lifecycle`.
- **Data & Resources**: Component metadata lives under `src/main/resources`; heavy datasets (e.g., `neqsim_component_names.txt`) must remain synchronized with thermodynamic model expectations before publishing new components.
- **Logging & Diagnostics (MANDATORY)**: log4j2 powers runtime logging, and all Java logging/output must use a logger (`org.apache.logging.log4j.Logger`). **NEVER** introduce `System.out.println` or `System.err.println` in Java code (including tests, examples, and generated snippets). Use parameterized logger calls such as `logger.info("message {}", value)`.
- **Build & Test Workflow**: Use `./mvnw install` for a full build (Windows: `mvnw.cmd install`); run the entire suite with `./mvnw test` and checkstyle/spotbugs/pmd with `./mvnw checkstyle:check spotbugs:check pmd:check`.
- **Python Runtime (MANDATORY)**: Use the Python interpreter explicitly selected by the user or parent workflow. If none was selected, use `C:\appl\neqsim-venv\Scripts\python.exe`. In agent command examples, `<python-executable>` means that selected absolute path. Child processes must reuse the same executable or `sys.executable`. Do not open interpreter selection, invoke bare `python`/`py`/`pip`/`pytest`, create or activate per-agent environments, reinstall or reconfigure the shared environment, or silently fall back to another interpreter. Report an unavailable executable or missing package as a blocker before changing runtimes.
- **Focused Tests**: Use the Maven `-Dtest` flag to run individual classes or methods; this keeps solver regressions quick to triage.
- **Style & Formatting**: Java code follows Google style with project overrides from `.config/checkstyle_neqsim.xml` and formatter profiles (`.config/neqsim_formatter.xml`); keep indentation at two spaces and respect existing comment minimalism.
- **Code Formatting (Spotless) - MANDATORY**: AI-generated Java is NOT auto-formatted. After creating or editing ANY `.java` file, run `./mvnw spotless:apply` (Windows: `mvnw.cmd spotless:apply`) to reformat to the project style, then `git add` the changes before committing. CI runs `./mvnw spotless:check` and FAILS the build on any unformatted file. Do not rely on local pre-commit hooks being installed, and NEVER bypass the gate with `git commit --no-verify`.
- **Serialization & Copying**: Many equipment classes rely on Java serialization (`ProcessEquipmentBaseClass.copy()`); avoid introducing non-serializable fields or mark them `transient` to preserve cloning. SpotBugs enforces this via the SE_BAD_FIELD rule. When adding fields to any `Serializable` class (equipment, measurement devices, mechanical design, thermo phases), use the correct modifier order: `private transient Type field;` or `private final transient Type field;`. Common non-serializable types that need `transient`: `Function`, `BiConsumer`, `Consumer`, `Thread`, JDBC `Connection`/`Statement`, Apache Commons Math interpolators, and any inner class that doesn't implement `Serializable`. The `ProcessLogic` interface extends `Serializable`.
- **External Dependencies**: Core math depends on EJML, Commons Math, JAMA, and MTJ; check numerical stability when swapping linear algebra routines, and keep JSON/YAML handling aligned with gson/jackson versions pinned in pom.xml.
- **Java 8 Compatibility (MANDATORY)**: See the critical section at the top of this document. All code MUST compile with Java 8. The CI build will FAIL if you use Java 9+ features like `String.repeat()`, `var`, `List.of()`, etc.
- **Sample Flow**:

```java
SystemInterface gas = new SystemSrkEos(216.0, 30.0);
gas.addComponent("methane", 0.5);
gas.setMixingRule("classic");
Stream feed = new Stream("feed", gas);
feed.setFlowRate(100.0, "kg/hr");
feed.run();
DistillationColumn column = new DistillationColumn("Deethanizer", 5, true, false);
column.addFeedStream(feed, 5);
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
column.run();
```
- **Test Authoring Tips**: Place new tests under the matching feature package (see docs/wiki/test-overview.md) and assert on physical outputs or solver residuals rather than internal arrays to keep tests resilient.
- **Regression Safety**: When modifying solver logic or property correlations, capture baseline values in tests and drop CSV/JSON fixtures into `src/test/resources` instead of hardcoding magic numbers in code.
- **Documentation Touchpoints**: Update README sections or docs/wiki entries when adding new models; the docs mirror the package layout and help downstream consumers understand new unit operations.
- **Community Norms**: Engage on GitHub issues or discussions for design questions; NeqSim has an active user base familiar with thermodynamics and process simulation who can provide valuable insights.
- **Performance Considerations**: Profile long-running simulations with Java Flight Recorder or VisualVM; optimize critical loops in thermodynamic calculations but prioritize clarity and maintainability in the codebase.
- **JavaDoc Standards (MANDATORY)**: ALWAYS document ALL classes and methods (public, protected, AND private) with complete JavaDoc. The Maven JavaDoc plugin checks all methods. Required elements: (1) class-level description with `@author` and `@version`, (2) method description, (3) `@param` for EVERY parameter with type and valid range, (4) `@return` describing what is returned (for non-void methods), (5) `@throws` for each exception. Before completing any code change, verify JavaDoc is complete and accurate. Update JavaDoc when modifying method signatures. Private methods also require complete JavaDoc with all @param and @return tags.

### JavaDoc HTML5 Compatibility (MANDATORY)

When writing JavaDoc, ensure HTML5 compatibility for the Maven JavaDoc plugin:

#### Tables
- **ALWAYS** include `<caption>` element after `<table>` tag
- **NEVER** use the `summary` attribute (deprecated in HTML5)
- Correct format:
```java
/**
 * <table>
 * <caption>Description of table contents</caption>
 * <tr><th>Header</th></tr>
 * <tr><td>Data</td></tr>
 * </table>
 */
```

#### @see Tags
- **NEVER** use `@see` with plain text like `@see IEC 61508` - this causes "reference not found" errors
- Only use `@see` with valid Java references: `@see ClassName`, `@see #methodName`, `@see package.ClassName#method`
- For standards references, put them in the description text instead:
```java
/**
 * Implements safety functions per IEC 61508 and IEC 61511 standards.
 */
```

#### Common JavaDoc Errors to Avoid
| Error | Cause | Fix |
|---|---|---|
| "no summary or caption for table" | Missing `<caption>` | Add `<caption>` after `<table>` |
| "attribute not supported in HTML5: summary" | Using `summary=""` on table | Remove `summary` attribute |
| "reference not found" | Invalid `@see` reference | Use valid class/method reference or move to description |
| "no @param for X" | Missing parameter documentation | Add `@param X description` |
| "no @return" | Missing return documentation | Add `@return description` |
| "no @throws for X" | Method throws exception without doc | Add `@throws X description` |
| "unexpected end tag" | Mismatched HTML tags like extra `</p>` | Check tag nesting, remove orphan closing tags |
| "semicolon missing" | Malformed HTML in JavaDoc | Check HTML tag closure |
| "bad use of '>'" | Lambda arrow `->` or comparison `>` in JavaDoc | Use `&gt;` for `>` or rewrite lambdas as anonymous classes |

#### Methods with throws Clause (CRITICAL)
- **EVERY** method with a `throws` clause MUST have `@throws` documentation for each exception
- This applies to ALL methods including private methods
- Format:
```java
/**
 * Writes data to the output.
 *
 * @param out the appendable to write to
 * @param data the data to write
 * @throws IOException if an I/O error occurs during writing
 */
private void writeData(Appendable out, String data) throws IOException {
```

#### HTML Tag Nesting
- **NEVER** have orphan closing tags (e.g., `</p>` without matching `<p>`)
- In JavaDoc HTML, starting a `<ul>` or `<ol>` implicitly closes the current paragraph. Do **NOT** add `</p>` after the list unless you explicitly open a new `<p>` afterward.
- Check that `<ul>` lists end with `</ul>`, not `</p>`
- Common mistake: ending a list with `</ul></p>` when there's no opening `<p>` after the list
- Wrong:
```java
/**
 * <ul>
 * <li>Item one</li>
 * </ul>
 * </p>
 */
```
- Correct:
```java
/**
 * <ul>
 * <li>Item one</li>
 * </ul>
 */
```

#### Lambda Expressions in JavaDoc Examples
- **NEVER** use lambda arrow syntax (`->`) in JavaDoc code examples - it causes HTML parsing errors
- Instead, use anonymous inner class syntax or escape the arrow
- Wrong: `list.forEach(item -> doSomething(item));`
- Correct: `list.forEach(new Consumer() { public void accept(Object item) { ... } });`
- For comparisons, use `&gt;` entity: `if (value &gt; threshold)`

#### Verification
Before committing, run `./mvnw javadoc:javadoc` to catch JavaDoc errors early. A common failure is an orphan `</p>` after a `<ul>` or `<ol>`, because lists implicitly close the current paragraph in JavaDoc HTML.

- **Java 8 Features**: All new code must be Java 8 compatible; use streams, lambdas, and `Optional` where they enhance readability. NEVER use `String.repeat()` - use `StringUtils.repeat()` from Apache Commons. NEVER use `var`, `List.of()`, `Map.of()`, text blocks, or any Java 9+ syntax. See the critical Java 8 Compatibility section at the top of this document for complete list.
- **Validation Framework**: Use `SimulationValidator.validate(object)` before running simulations to catch configuration errors early. When extending equipment, override `validateSetup()` to add custom validation. See `neqsim.util.validation` package and docs/integration/ai_validation_framework.md.
- **AI-Friendly Error Handling**: Exceptions in `neqsim.util.exception` provide `getRemediation()` hints. When adding new errors, include actionable fix suggestions that AI agents can parse.
- **Troubleshooting**: When simulations fail (flash non-convergence, zero properties, recycle divergence), consult the `neqsim-troubleshooting` skill for ranked recovery strategies before retrying blindly.
- **Input Validation**: Before creating NeqSim objects, validate inputs using the `neqsim-input-validation` skill - catches physically impossible temperatures, pressures, compositions, and wrong component names.
- **Regression Baselines**: When modifying solver logic or property correlations, capture baseline values FIRST using the `neqsim-regression-baselines` skill. This prevents silent accuracy drift.
- **Standards Lookup**: For any engineering task, identify applicable industry standards using the `neqsim-standards-lookup` skill. It maps equipment types to standards (API, NORSOK, DNV, ISO, ASME), provides CSV database query patterns, and defines the `standards_applied` schema for results.json. Standards compliance is mandatory for all task scales.
- **Plant Data Integration**: When connecting NeqSim models to plant historian data (OSIsoft PI, Aspen IP.21), use the `neqsim-plant-data` skill for tagreader API patterns, tag mapping, digital twin loops, and data quality handling. See also the `@plant-data` agent.
- **Model Calibration and Data Reconciliation**: When reducing model-vs-plant mismatch, tuning parameters with bounded optimization, reconciling noisy measurements, or producing train/validation fit reports, use the `neqsim-model-calibration-and-data-reconciliation` skill.
- **API Changelog**: Check `CHANGELOG_AGENT_NOTES.md` in the repo root for recent API changes, new classes, deprecated methods, and known method name corrections.
- **Capability Assessment**: Before starting complex engineering tasks, use the `@capability-scout` agent or the `neqsim-capability-map` skill to identify what NeqSim can do, find gaps, and plan implementations. The result MUST be saved to `step1_scope_and_research/capability_assessment.md` (mandatory artifact for Standard/Comprehensive tasks).
- **Skill Discovery**: Run `python devtools/skill_search.py "<task title>" --top 5` at the start of any task to surface the most relevant skills via TF-IDF over the SKILL.md `description` fields. Prefer this over manual lookup in `skill-index.json` (which is now a curated short-list, not exhaustive).
- **Agent Discovery**: Run `python devtools/agent_search.py "<task title>" --top 8 --json --out step1_scope_and_research/agent_plan.json` to rank the best specialist agents across all repos (neqsim + community + enterprise) — the search output lists the skills each agent loads. Record the chosen agents and the composition/workflow in `capability_assessment.md` §4b/§4c and mirror it into `results.json` `agent_workflow_plan`. Prefer delegating to a specialist agent (so its governance and internal workflow are reused) over re-loading its skills manually. For tasks spanning ≥3 disciplines, compose a declarative workflow via MCP `composeWorkflow`/`composeMultiServerWorkflow` or an `engineering-harness` study instead of a single agent.
- **Literature & Document Pull**: Use `@literature-scout` to fetch papers, standards, and internal STID/vendor docs into `step1_scope_and_research/references/`. The agent writes `references/manifest.json` and summarises sources into `notes.md`.
- **Pre-PR Quality Gate**: Before opening a PR for a task, invoke `@review <task folder>` (read-only). It wraps `validate_task_results.py`, `consistency_checker.py`, capability-assessment presence, figure→discussion traceability, and repo-memory hits, returning a PASS/WARN/FAIL grade.
- **Skills/Agents CI Lint**: `.github/workflows/skills_agents_lint.yml` runs `devtools/verify_skills_agents.py` (front-matter + skill-index reference check) and `devtools/generate_agent_skill_map.py` (auto-generates `docs/development/AGENT_SKILL_MAP.md`) on every PR touching `.github/skills/` or `.github/agents/`. The map is rebuilt from `Loaded skills:` lines in agent files; CI fails if the committed map is stale.
- **Flow Assurance**: For hydrate, wax, asphaltene, corrosion, or pipeline hydraulics analyses, use the `neqsim-flow-assurance` skill for comprehensive patterns covering all flow assurance threats with NeqSim code patterns. Rigorous CO2 corrosion from a brine uses `NorsokM506ElectrolyteBridge` (electrolyte pH + FeCO3 film); per-segment corrosion+scale profiles use `PipeSegmentIntegrity`; mineral scale uses `ElectrolyteScaleCalculator` / `ScaleKinetics` / `BrineMixingScaleEvaluator`. See also the `@flow-assurance` agent.
- **Production Chemistry**: For chemical selection and dosing (scale/corrosion/hydrate/wax/asphaltene inhibitors, H2S and oxygen scavengers, demulsifier, biocide, pH adjuster), cocktail compatibility, minimum effective dose, scavenger breakthrough, oil-in-water dose optimisation, or chemical root-cause of a deposit, use the `neqsim-production-chemistry` skill (`neqsim.process.chemistry`, `neqsim.process.equipment.watertreatment`). Flow assurance sizes the threat; production chemistry sizes the chemical. See also the `@production-chemistry` agent.
- **Water/Liquid Hammer**: For fast valve closure, ESD, pump trip, check-valve slam, hydraulic surge, or STID/tagreader-based surge screening, use the `neqsim-water-hammer` skill. Prefer `WaterHammerStudy` and MCP `runWaterHammer` for complete workflows that combine route geometry, field-data overrides, event schedules, pressure envelopes, and design-pressure validation.
- **CCS and Hydrogen**: For CO2 capture/transport/storage or hydrogen systems (blending, electrolysis, blue/green H2), use the `neqsim-ccs-hydrogen` skill for CO2 phase behavior, impurity management, injection well analysis, and H2 pipeline design. See also the `@ccs-hydrogen` agent.
- **Power Generation**: For gas turbines, steam turbines, HRSG, or combined cycle systems, use the `neqsim-power-generation` skill for equipment patterns and efficiency calculations.
- **Platform Process Modeling**: For building full topside process models of oil & gas platforms (FPSO, fixed, semi-sub) from design documents or operational data, use the `neqsim-platform-modeling` skill. Covers multi-stage separation with oil recycles, recompression trains with compressor curves and anti-surge, export/injection compression, scrubber liquid recovery, Cv-based valve flow, iteration strategies, and structured result extraction. Derived from 15+ production NCS platform models.
- **Technical Document and Image Reading**: For extracting data from PDFs, Word docs, Excel files, and engineering images (P&IDs, mechanical drawings, vendor API datasheets, compressor maps, phase envelopes), use the `neqsim-technical-document-reading` skill. Use `devtools/pdf_to_figures.py` to convert PDF pages to PNG images, then `view_image` for multimodal analysis of engineering drawings. See also the `@read technical documents` agent. The skill includes structured extraction patterns for P&ID topology (equipment/valve/instrument tags, piping), vendor datasheet operating conditions, mechanical arrangement dimensions, material certificates, trapped-liquid rupture evidence packs, and performance map digitization.
- **Vendor Document Retrieval**: For retrieving vendor documents (compressor curves, mechanical drawings, data sheets) for engineering tasks, use the `neqsim-stid-retriever` skill. Supports local directories, manual upload to `references/`, and pluggable retrieval backends (configured via gitignored `devtools/doc_retrieval_config.yaml`). Documents are classified by type, filtered by relevance to the task, and fed into the `neqsim-technical-document-reading` pipeline for data extraction.
- **Trapped-Liquid Fire Rupture Studies**: For blocked-in liquid, trapped liquid, thermal expansion rupture, no relief, flange/pipe rupture under fire, or PFP-demand studies, use the `neqsim-trapped-liquid-fire-rupture` skill. Retrieve P&IDs/STIDs, line lists, piping specs, material certificates, flange/bolt/gasket data, fire-zone/PFP documents, relief basis, and acceptance criteria before running `neqsim.process.safety.rupture` calculations. Report missing final-design evidence explicitly in `results.json` assumptions/gaps.
- **Fire-Water and Deluge Coverage**: For deluge coverage adequacy, fire-water demand, nozzle count and spacing, fire-monitor substitution, or "can PFP replace deluge" questions, use the `neqsim-firewater-deluge-design` skill and `neqsim.process.safety.firewater`. Take the fire type and heat flux from `neqsim-consequence-analysis` and the blowdown time from `neqsim-depressurization-mdmt` first — blowdown is the primary barrier for a pressurised inventory. Use these classes rather than `FireProtectionDesign.firewaterDemand(...)`, which is a lumped deliverables figure with no coverage or nozzle logic.
- **Auto-Validation for New Equipment**: When creating a new class that extends `ProcessEquipmentBaseClass`, ALWAYS generate a `validateSetup()` method that checks: (1) required input streams are connected, (2) required parameters are set and within valid ranges, (3) return `ValidationResult` with remediation hints for each issue.
- **Equipment Design Feasibility Reports**: After running compressors or heat exchangers in a process simulation, use the Design Feasibility Report classes to assess if equipment is realistic to build and operate. `CompressorDesignFeasibilityReport` (API 617 + cost + 15 OEM suppliers + curve generation) and `HeatExchangerDesignFeasibilityReport` (TEMA/ASME + cost + 14 HX suppliers) produce FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE verdicts and comprehensive JSON reports. See `neqsim-api-patterns` skill for usage patterns.
- **Auto-Annotation for Public Methods**: When adding new public methods to core classes (SystemInterface, ProcessEquipmentInterface), consider adding `@AIExposable` annotation with description, category, example, and `@AIParameter` annotations documenting valid ranges/options.
- **Jupyter Notebook Examples**: When creating Jupyter notebook examples, ensure they run end-to-end and reflect the latest API changes; place them in the `notebooks/` directory and link to them from the main documentation. Follow the neqsim-python direct Java API bindings as shown at https://github.com/equinor/neqsim-python?tab=readme-ov-file#4-direct-java-access-full-control
- **Add markdown files with documentation**: When adding documentation as markdown files:
  1. Update `REFERENCE_MANUAL_INDEX.md` with the new file entry
  2. Update the relevant section's `index.md` (e.g., `docs/examples/index.md`)
  3. Verify ALL links to other docs using `file_search` before adding them
  4. See "Documentation Links (MANDATORY)" section below for link guidelines

### Markdown Documentation Guidelines (MANDATORY)

#### Jekyll Front Matter (REQUIRED for Search)

**ALL markdown documentation files in `docs/` MUST have Jekyll YAML front matter** at the very beginning of the file for proper search indexing. Without front matter, files may not appear in search results with proper titles.

**Required format:**
```yaml
---
title: Your Document Title
description: A concise description (1-2 sentences) of what the document covers. Include key terms users might search for.
---
```

**Example for a thermodynamics guide:**
```yaml
---
title: Reading Fluid Properties in NeqSim
description: Comprehensive guide to calculating and reading thermodynamic and physical properties from fluids, phases, and components. Covers init levels, TPflash, density, enthalpy, viscosity, units, volume translation, and JSON reports.
---
```

**Rules:**
1. Front matter MUST be the first thing in the file (before any content)
2. Use three dashes `---` to delimit the YAML block
3. `title` should be descriptive but concise (appears in search results)
4. `description` should include searchable keywords relevant to the content
5. Do NOT duplicate the title as an H1 heading immediately after front matter (Jekyll handles this)
6. **CRITICAL: Quote values containing colons** - In YAML, colons have special meaning. If your title or description contains a colon (`:`), wrap the entire value in double quotes:
   ```yaml
   # WRONG - causes YAML parse error:
   title: PVT Workflow: From Lab Data to Model
   description: This guide covers: setup, configuration, and testing.

   # CORRECT - quoted values:
   title: "PVT Workflow: From Lab Data to Model"
   description: "This guide covers: setup, configuration, and testing."
   ```
7. **Avoid trailing colons** - Don't end descriptions with a colon (e.g., `description: "Features include:"`) - complete the sentence instead

**Files that already have front matter:** Check if they have both `title` and `description`. If missing `description`, add it.

When creating or editing markdown documentation files:

#### HTML and Markdown Mixing Rules

**NEVER mix markdown syntax inside HTML block elements.** Many markdown parsers don't process markdown inside `<div>` tags.

| Problematic Pattern | Why It Fails | Solution |
|---|---|---|
| `<div>` containing markdown tables (`\|---\|`) | Parser ignores markdown inside HTML blocks | Use pure markdown OR pure HTML |
| `<div>` containing numbered lists (`1. Item`) | Lists don't render as lists | Remove div wrapper or use `<ol><li>` |
| `<div>` containing bullet lists (`- Item`) | Lists don't render as lists | Remove div wrapper or use `<ul><li>` |

#### Correct Patterns

**For styled content boxes, choose ONE approach:**

1. **Pure Markdown (preferred for tables/lists):**
   ```markdown
   ### Section Title

   **Heading text:**

   | Column 1 | Column 2 |
   |----------|----------|
   | Data     | Data     |

   > *Note: Use blockquotes for callouts*
   ```

2. **Pure HTML (for complex styling):**
   ```html
   <div style="background: #e8f5e9; padding: 1rem;">
   <h4>Title</h4>
   <ul>
   <li>Item one</li>
   <li>Item two</li>
   </ul>
   </div>
   ```

#### Table Formatting

- Always include a blank line before and after tables
- Use consistent column separator widths: `|----------|` not `|---|`
- Ensure header separator row has same column count as data rows

#### List Formatting

- Always include a blank line before numbered/bullet lists
- For nested content after bold headers, add a blank line:
  ```markdown
  **Suggested Approach:**

  1. **Step one:** Description here
  2. **Step two:** Description here
  ```

#### LaTeX Math Equations (MANDATORY for KaTeX Rendering)

The documentation site uses **KaTeX** for math rendering. Use the correct delimiters to ensure equations render properly.

##### Display Math (Block Equations)

**USE `$$...$$`** for display/block equations:

```markdown
The cubic equation of state:

$$
P = \frac{RT}{v - b} - \frac{a(T)}{(v + \epsilon b)(v + \sigma b)}
$$

Where $P$ is pressure and $T$ is temperature.
```

**NEVER use `\[...\]`** - these delimiters are often stripped by markdown processors and render as plain text like `[ P = \frac{RT}{v-b} ]`.

##### Inline Math

**USE `$...$`** for inline math:

```markdown
The acentric factor $\omega$ affects the alpha function $\alpha(T_r, \omega)$.
```

**NEVER use `\(...\)`** - these are less reliably rendered.

##### Common LaTeX Mistakes to Avoid

| Wrong | Correct | Issue |
|---|---|---|
| `\[ P = \frac{RT}{v-b} \]` | `$$ P = \frac{RT}{v-b} $$` | `\[...\]` stripped by parser |
| `\(T_r\)` | `$T_r$` | `\(...\)` less reliable |
| `$$ P = ... $$ where` | `$$ P = ... $$` + newline + `where` | No text on same line as `$$` |
| Equation inside `<div>` | Move equation outside HTML block | Markdown not processed in HTML |

##### Verification

After adding equations, preview locally or check that:
1. Display equations appear centered on their own line
2. Inline math renders within the text flow
3. No raw LaTeX syntax (backslashes, braces) appears in rendered output

#### Documentation Links (MANDATORY)

When adding links to other documentation files, follow these rules to prevent broken links:

##### Link Verification Rules

1. **ALWAYS verify target files exist** before adding links:
   - Use `file_search` to confirm the file exists in the repository
   - Check the exact path and filename (case-sensitive on some systems)

2. **Use correct relative paths** based on the source file location:
   - From `docs/fielddevelopment/` to `docs/process/`: use `../process/filename.md`
   - From `docs/examples/` to `docs/tutorials/`: use `../tutorials/filename.md`
   - Within same folder: use just `filename.md`

3. **Prefer existing documentation** over creating placeholder links:
   - If a linked file doesn't exist, either create it OR link to an existing alternative
   - NEVER add links to files that don't exist

##### Common Documentation Paths

| Documentation Area | Path | Example Files |
|---|---|---|
| Process equipment | `docs/process/` | `separators.md`, `compressors.md`, `heat-exchangers.md` |
| Field development | `docs/fielddevelopment/` | `pressure_boundary_optimization.md`, `CAPACITY_CONSTRAINT_FRAMEWORK.md` |
| Thermodynamics | `docs/thermo/` | `equations-of-state.md`, `flash-calculations.md` |
| Examples | `docs/examples/` | `*.ipynb`, `*.java`, `index.md` |
| Tutorials | `docs/tutorials/` | Getting started guides |
| Troubleshooting | `docs/troubleshooting/` | Common issues and solutions |

##### When Adding New Documentation

1. **Update index files** when creating new documentation:
   - Add entry to `docs/REFERENCE_MANUAL_INDEX.md` (master index of 360+ files)
   - Add entry to the relevant section's `index.md` (e.g., `docs/examples/index.md`)

2. **Cross-reference related docs** with verified links:
   ```markdown
   ## Related Documentation

   - [Pressure Boundary Optimization](pressure_boundary_optimization.md)
   - [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
   ```

3. **For Jupyter notebooks**, also add to:
   - `docs/examples/index.md` - Examples index
   - `docs/REFERENCE_MANUAL_INDEX.md` - Master reference

##### Link Format Examples

```markdown
<!-- Same directory -->
[Related Topic](related-topic.md)

<!-- Parent directory -->
[Process Overview](../process/index.md)

<!-- Sibling directory -->
[Tutorial](../tutorials/getting-started.md)

<!-- Link to specific section -->
[VFP Tables](pressure_boundary_optimization.md#vfp-generation)

<!-- Link to Java example -->
[Java Example](MultiScenarioVFPExample.java)

<!-- Link to notebook -->
[Notebook Tutorial](ProductionSystem_BottleneckAnalysis.ipynb)
```

##### Broken Link Prevention Checklist

Before finalizing documentation with links:

- [ ] All linked `.md` files exist (use `file_search("**/filename.md")`)
- [ ] All linked `.ipynb` notebooks exist
- [ ] All linked `.java` examples exist
- [ ] Relative paths are correct for source file location
- [ ] Index files updated for new documentation
- [ ] No links to planned-but-not-created files

### Mechanical Design & Well Design (MANDATORY)

> **Full patterns are in skills - load them before implementing:**
> - `neqsim-api-patterns` - Equipment design feasibility reports, separator mechanical design, cost estimation
> - `neqsim-subsea-and-wells` - Well casing design (API 5C3), SURF cost, barrier verification (NORSOK D-010)
> - `neqsim-standards-lookup` - Industry standards mapping (ASME, API, DNV, ISO, NORSOK)
>
> **Architecture:** MechanicalDesign class + DataSource class + Calculator class + JSON reporting.
> Physical dimensions and internals are configured through `SeparatorMechanicalDesign`, NOT directly on `Separator`.
> See `AGENTS.md` "Separator MechanicalDesign Architecture" and "Well Mechanical Design" sections for details.

<!--
  The ~500 lines of mechanical design code templates and well design patterns that were here
  have been moved to the neqsim-api-patterns, neqsim-subsea-and-wells, and neqsim-standards-lookup
  skill files. Load those skills before implementing mechanical design.
-->
  - Location: `designdata/MaterialPipeProperties.csv`, `designdata/MaterialPlateProperties.csv`, etc.
  - Required columns: `MaterialGrade`, `SMYS_psi`, `SMTS_psi`, `Density_kg_m3`, `Standard`

- **TechnicalRequirements_Process.csv**: Equipment-specific design parameters by company
  - Required columns: `Company`, `EquipmentType`, `ParameterName`, `Value`, `Unit`, `Standard`

- **TechnicalRequirements_Piping.csv**: Piping code-specific design values
  - Required columns: `Code`, `ParameterName`, `Value`, `Unit`, `Description`

- **Standards Tables** (in `designdata/standards/` subdirectory):
  - `api_standards.csv` - API standard parameters
  - `asme_standards.csv` - ASME code requirements
  - `dnv_iso_en_standards.csv` - DNV/ISO/EN requirements
  - `norsok_standards.csv` - NORSOK requirements
  - `standards_index.csv` - Index mapping equipment types to applicable standards

---

### Jupyter Notebook Creation Guidelines

> **Full patterns are in the `neqsim-notebook-patterns` skill.** Load it before creating notebooks.

**Key rules (always apply):**
- Use `devtools/neqsim_dev_setup.py` for task notebooks and runner workflows:
   call `neqsim_init(project_root=PROJECT_ROOT, ...)`, then use classes through
   `ns.*` or `ns.JClass(...)`. Do not use `from neqsim import jneqsim` in
   repository task notebooks because it can load a stale installed package.
- Temperature in **Kelvin**, pressure in **bara** by default in Java API
- **Always** set mixing rule: `fluid.setMixingRule("classic")`
- Call `fluid.initProperties()` after flash before reading transport properties
- **Every notebook MUST be executed** — use NeqSim Runner by default for task
   notebooks; unexecuted notebooks are incomplete
- Include **2-3 matplotlib figures** with axis labels, units, titles, legends, grids
- Save results to `results.json` in the task folder

See `AGENTS.md` "Jupyter Notebook Creation Guidelines" section for common class import paths and the full getting-results reference.

### Task-Solving Workflow (MANDATORY)

**Task destination override:** For new tasks use `neqsim --show-task-root`.
Resolution is explicit `--task-root PATH` > `NEQSIM_TASK_ROOT` > the saved
`~/.neqsim/task_defaults.json` default > repository `task_solve/`.
Set it with `neqsim --set-task-root "PATH"` (or `cwd` to follow the terminal's
folder); reset it with `neqsim --reset-task-root`. Literal `task_solve/` paths in
these instructions are examples under the resolved root, not a forced repository location.
Pass the created absolute task path to child agents, tools and validators; keep
all artifacts under it. Resume existing tasks in place. For external task folders,
set `NEQSIM_PROJECT_ROOT` to the source repository and `NEQSIM_TASK_DIR` to the
active task when needed. Report destination failures instead of silently falling back.

**Source documents:** the document root is **optional** - either set or undefined.
When set, that folder **and all its subfolders** are the source library for every
task: `neqsim --set-document-root "PATH"`, `neqsim --show-document-root`,
`neqsim --reset-document-root`, and `neqsim documents [PATTERN]` to search it
recursively. Precedence: explicit path > `NEQSIM_DOCUMENT_ROOT` > saved
`document_root` in `~/.neqsim/task_defaults.json` > none. Every new task records
the resolved value as `inputs.document_root` in its `study_config.yaml` (empty
when undefined), and lists the library's files in
`step1_scope_and_research/references/document_root_index.md`, so a resumed task
and every child agent see what is available without running the CLI (refresh it
with `neqsim documents --index <task_dir>`). Search it before declaring
a standard, datasheet or drawing
unavailable; when undefined, work from user-supplied documents and log a data
gap. It is read-only: copy the documents a task uses into that task's
`step1_scope_and_research/references/<source>/` instead of writing there.
Plant P&IDs, data sheets and drawings come from the retrieval backend (STID),
not the document root: run `neqsim fetch-docs <task_dir>` (automatic in
`new-task` and Standard-first living cycles) and report the status in
`references/stid/retrieval_status.json` before declaring them unavailable.

> **Full workflow is in `docs/development/TASK_SOLVING_GUIDE.md`.** Read it before starting any task.
> Past solved tasks are indexed in `docs/development/TASK_LOG.md` — search before starting from scratch.

**Key rules (always apply):**
1. **Create task folder FIRST:** `neqsim new-task "title" --type X --author "Name"`
   If the terminal reports `neqsim` is not recognized, the console script is not
   on PATH in that shell — re-run the identical command as
   `<python-executable> -m neqsim_cli ...` using the interpreter from the Python
   Runtime rule above. Same entry point, same arguments; applies to every
   `neqsim ...` command below. Never skip the step or hand-create the folder.
2. **All output goes to** `task_solve/YYYY-MM-DD_slug/` — never to `examples/`, `docs/`, or workspace root
3. **All downloaded documents** go inside the task folder at `step1_scope_and_research/references/`, filed into **per-source subfolders** (`stid/`, `pepr/`, `tr2000/`, `maintenance/`, `servicenow/`, `tagreader/`, `seeq/`, `rigga/`, `vendor/`, `lab/`, `literature/`, `web/`, `manual/`). Run `python devtools/generate_sources_md.py task_solve/YYYY-MM-DD_slug --organize` to file loose docs and (re)build the distributable `references/SOURCES.md` + `references/collection_manifest.json` so the whole task folder can be handed to others. The manifest also drives the report's **Information Sources and Evidence Basis** section (how many documents came from STID, SAP, PEPR, …), so regenerate it before the report.
3b. **State every assumption, especially where data could not be found.** Put `assumptions` and `data_gaps` in results.json (gap fields: `gap`/`blocker`, `source`, `status`, `assumed` — what was used instead — and `effect`). They render as the report's **Assumptions and Data Gaps** section, and the gate warns when a declared source system has no captured evidence but nothing is registered.
4. Follow the 3-step workflow: **Scope & Research** → **Analysis & Evaluation** → **Report**
5. **Benchmark validation (MANDATORY):** Compare NeqSim results against independent reference data
6. **Uncertainty analysis:** Monte Carlo with P10/P50/P90 + tornado diagram (MANDATORY for Standard/Comprehensive tasks with economics or reserves; optional for Quick tasks)
7. **Risk evaluation:** Risk register with ISO 31000 5×5 matrix (MANDATORY for Standard/Comprehensive tasks; optional for Quick tasks)
8. **Consistency check:** Run `python devtools/consistency_checker.py` before generating reports
8b. **Report files are named after the report title.** Set `study.title` in `study_config.yaml` (or pass `--title`) before generating: a study titled "Hydrate margin for the export line" ships `step3_report/Hydrate_margin_for_the_export_line.docx` + `.html`, so the deliverable is identifiable outside its task folder. Files written under an earlier title are deleted on regeneration — never leave a superseded `Report.docx` beside the current one.
8c. **Report language is English unless configured.** Set `report.language` in `study_config.yaml` (`en` default, `nb`/`no` for Norwegian, any ISO code); `--language CODE` / `NEQSIM_REPORT_LANGUAGE` override it for one run. It translates the generator's headings, cover labels, and caption prefixes and sets the document language for Word spell-check — write `results.json` and `task_spec.md` in the same language. The scientific paper stays English.
9. **Work record (generated with every report):** `generate_report.py` / `neqsim report` also writes `step3_report/WORK_RECORD.md` — what was done, how, which data and scripts were used, and where every file lives. Fill its `background`, `method`, and `limitations` narrative blocks by hand (preserved on regeneration); verify with `neqsim work-record <task> --check`.
10. **Improve the tooling (MANDATORY, every task):** a task is also a test of NeqSim, the agents and the skills. If solving it needed a workaround, a rediscovery, or more than one trial-and-error loop that a NeqSim class / agent / skill should have handled, fix it as part of this task — Java + JUnit + `spotless:apply` + PR for a NeqSim gap, a `SKILL.md` / `*.agent.md` edit for a tooling gap, and a recorded composition pattern for a new useful multi-agent pipeline. **Commit and push each fix to the repo that owns it** (neqsim / community-skills / community-agents / enterprise-skills / enterprise-agents) — one branch and PR per repo; an unpushed fix is lost. Never commit task output or company data to a code repo. Log every change in `step1_scope_and_research/neqsim_improvements.md` **and** in `results.json` `improvements`; state explicitly when nothing needed changing. Never ask permission for this step.
11. **After completing:** Add entry to `docs/development/TASK_LOG.md`

#### Task Log Entry Format

**Privacy rule:** Task log entries are public/reusable memory. Do not include
company/operator names, field/facility/asset names, equipment tag numbers,
internal document names, private system names, access diagnostics, or task folder
slugs containing those details. Use generic descriptors and `private task folder
(redacted)` for confidential task outputs.

```markdown
### YYYY-MM-DD — Short task title
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design) | G (Workflow) | G (Workflow)
**Keywords:** comma, separated, search, terms
**Solution:** path/to/test/or/notebook
**Notes:** Key decisions, gotchas, or results
```

