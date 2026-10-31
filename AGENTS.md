# NeqSim — Agent Instructions

> Read automatically by VS Code Copilot, OpenAI Codex, Claude Code and other agents.
> This file is kept short on purpose because it is resent on every model call.
> **It is already in your context — do not `read_file` it.** Detail lives in:
> - [docs/development/AGENT_REFERENCE.md](docs/development/AGENT_REFERENCE.md) — full
>   workflow, `results.json` schema, Automation API, code patterns, key paths, JavaDoc
>   and documentation rules. Search it for the heading you need; never read it whole.
> - Skills in `.github/skills/` (find them with `uv run --project devtools --locked devtools/skill_search.py "<topic>"`).
> - [CONTEXT.md](CONTEXT.md) — 60-second repo map. `docs/development/CODE_PATTERNS.md` — starters.

## Hard constraints (CI fails otherwise)

- **Java 8 everywhere, tests included.** Never `var`, `List.of/Set.of/Map.of`,
  `String.repeat` (use `StringUtils.repeat`), `isBlank/strip/lines` (use `trim()`),
  text blocks, records, pattern-matching `instanceof`, `Optional.isEmpty()`.
- **Logging:** Log4j2 only (`private static final Logger logger = LogManager.getLogger(X.class);`,
  parameterized `logger.info("{}", v)`). Never `System.out/err.println`, also not in tests or examples.
- **Spotless:** after creating or editing ANY `.java` file run `.\mvnw.cmd spotless:apply`
  (Linux/Mac `./mvnw`), then `git add`. CI runs `spotless:check`. Never `--no-verify`.
- **JavaDoc on every class and method, private included:** description, `@param` for each
  parameter, `@return`, `@throws` for every declared exception, class `@author`/`@version`.
  HTML5: `<table>` needs `<caption>`, no `summary=`; `@see` only with Java references;
  no `</p>` after `</ul>`/`</ol>`; no `->` or raw `>` in JavaDoc (`&gt;`). Check with `mvnw javadoc:javadoc`.
  Spotless/Checkstyle/JavaDoc failures: `neqsim-code-hygiene` skill.
- **Serializable classes:** non-serializable fields (`Function`, `Consumer`, `Thread`, JDBC,
  interpolators) must be `private transient` (SpotBugs SE_BAD_FIELD).
- **Verify APIs before use:** find the class, read constructor/method signatures, never assume
  convenience overloads, enum locations or getter arguments.
- **Discover across all functionality:** in a source checkout use
  `uv run --project devtools --locked devtools/capability_search.py "<task or API>" --json`.
  It returns current source types, workflow agents/skills, guides and test references;
  load only the relevant skill sections. In plugin/MCP-only sessions use runtime discovery.
  Read [the discovery guide](docs/development/AGENT_CAPABILITY_DISCOVERY.md) before declaring
  functionality absent. Routing coverage and test references do not establish qualification.
- **Agent/MCP coverage:** `src/main/resources/neqsim/mcp/engineering-coverage.json` is a generated snapshot,
  not a source of truth. After Java formatting or agent/skill mapping changes, regenerate it with
  `uv run --project devtools --locked devtools/build_engineering_coverage.py` and stage the output.
  For merge conflicts, resolve source and registry conflicts first, then regenerate and `git add` this file.
  Never hand-merge its JSON or combine branch copies; neither produces a valid inventory of the merged sources.
  CI rebuilds and validates the snapshot, then commits changed output back to same-repository PR branches
  and `master` when permissions and branch protection allow. Fork PRs remain validation-only.
  Register new engineering operations with routes and evidence; source presence is not validation.
- **AI workflow names:** GitHub Actions workflows for agent, skill, MCP, task, or research
  tooling MUST use `ai-<purpose>.yml` in lowercase kebab-case, directly in `.github/workflows/`.
  Categorize by primary responsibility: publishing MCP artifacts still uses `publish-`.
  Follow [.github/WORKFLOWS.md](.github/WORKFLOWS.md); update filename references when renaming
  and preserve required-check names unless their consumers have been reviewed.
- **Documentation code must be test-verified:** every Java/Python snippet in docs gets a JUnit
  test (e.g. `DocExamplesCompilationTest`) that is run and passes before the doc is done.
- **Notebooks must be executed** end to end (NeqSim Runner by default); unexecuted = incomplete.
- **Every flow diagram must pass `pfd-layout-review` before it is used** (PFD/BFD/P&ID sketch/flow network in a report,
  paper or figure folder): `neqsim pfd-check <svg|layout.json>` must give PASS (no overlaps, labels on lines, crossings,
  missing arrowheads/tags; WARNs fixed or explained), then view the picture; fix the generator (`devtools/pfd_diagram.py`),
  never the PNG. Skill `neqsim-pfd-layout-review`.
- **Linux-only engines** (FluidMagic, OpenFOAM, OPM Flow) need Docker Desktop/WSL2 on Windows;
  run `neqsim docker ensure --linux` before container work to start installed Docker
  automatically and wait for readiness (source checkout:
  `uv run --project devtools --locked devtools/docker_runtime.py ensure --linux`).
  Never install/elevate, switch contexts or container modes, or stop Docker automatically.
  Report missing installations, startup timeouts and denied access as blockers, never skip silently.
- **FluidMagic is Equinor-internal, not on PyPI** (enterprise agents/skills only): running it needs the
  private repo `equinor/FluidMagic` (own GitHub SSO; `gh repo clone equinor/FluidMagic`,
  `pip install -e FluidMagic/fluidmagic`). Never `pip install fluidmagic`; if `import fluidmagic`
  fails, tell the user how to get it and fall back to the offline hand-off.

## Running calculations

- **MCP first:** for a single flash/PVT/process/standards/sizing calculation use a curated
  `mcp_neqsim_*` tool (confirm fields with `getSchema`/`validateInput`), then `runCapability`
  (`search` → `inspectApi` → invoke). Write Python/Java only for loops, plotting, state,
  notebooks, reports, or inside `/solve-task`. If NeqSim lacks a capability, add it in Java + tests.
- **Python:** use the selected interpreter, else `C:\appl\neqsim-venv\Scripts\python.exe`. Never
  bare `python`/`pip`/`pytest`, never create/activate/select environments; children reuse
  `sys.executable`. Missing package or interpreter = report the blocker.
- **Task notebooks** load workspace classes through `devtools/neqsim_dev_setup.py`
  (`ns = neqsim_init(project_root=...)`, then `ns.*` / `ns.JClass`); never `from neqsim import jneqsim`.
  Java not found → `uv run --project devtools --locked devtools/java_locator.py` / `ensure_java_home()` before declaring it missing.
- **Build/test:** `mvnw.cmd install`; single test `mvnw.cmd test -Dtest=Class#method`;
  static analysis `checkstyle:check spotbugs:check pmd:check`.
- **"Package and update python":** `.\mvnw.cmd package -DskipTests`, then copy
  `target\neqsim-<version>.jar` into `...\site-packages\neqsim\lib\` (flat, not a java11 subfolder).

## Physics and API gotchas

- **EJML-only numerical code:** Use primitive arrays at public numerical boundaries and EJML or
  `LinearAlgebraOperations` for calculations. Do not restore a JAMA dependency or reference backend.
  Former matrix-specific hooks and accessors now use EJML `SimpleMatrix`; downstream callers must migrate.
  Preserve dimensions, orientation, ownership and subclass-hook dispatch. EJML LU `setA` can accept a singular
  matrix: reject non-finite results. Pure-component phase-envelope points must satisfy the EOS pressure equation
  for both volume roots as well as fugacity equilibrium; a spinodal-stalled volume is not a saturation root.
- Always `setMixingRule(...)` (numeric for CPA); `createDatabase(true)` for new components.
  Temperatures in K and pressures in bara by default. `clone()` fluids before branching.
- After any flash call `fluid.initProperties()` before reading density/viscosity/conductivity;
  `init(3)` alone leaves transport properties at zero. Choosing `init(...)` levels in Java:
  `neqsim-thermodynamic-initialization` skill.
- Phase envelopes: classify branches physically (the branch holding the cricondentherm is the dew
  curve); do not trust getter/label names alone — see `neqsim-phase-envelope`.
- Multi-area plants: one `ProcessSystem` per area, combined in a `ProcessModel`
  (`plant.add("area", ps)`); never add a `ProcessModel`/`ProcessModule` to a `ProcessSystem`.
- Loops: prefer `process.makeRecycles()` / `setAutoRecycles(true)` to hand-written tears.
  `runUntilConverged(n)` self-tunes tolerances — do not set a tolerance "to be helpful".
- Agents drive models through `process.getAutomation()` (cached facade): string addresses,
  `getVariableValueSafe`/`setVariableValueSafe`, `evaluate(...)` as the never-throwing optimizer step
  gated on `feasible`, `getUtilizationSnapshot()` as the observation, `newOptimizer()` for
  `AgenticProcessOptimizer`. Details: `neqsim-agentic-process-optimization` skill.
- Separator/vessel geometry and internals go through `SeparatorMechanicalDesign`, not `Separator`.
- New equipment extends `ProcessEquipmentBaseClass`, implements `validateSetup()` with remediation hints.

## Solving engineering tasks

Use the `solve-task` agent; its workflow is the `neqsim-task-workflow` skill (load per phase).
Non-negotiables:

1. **Create the task folder first:** `neqsim new-task "title" --type X --author "Name" --prompt "<verbatim request>"`
   (`neqsim` not found → `<python> -m neqsim_cli ...`, same arguments). Root: `neqsim --show-task-root`
   (explicit > `NEQSIM_TASK_ROOT` > `~/.neqsim/task_defaults.json` > `task_solve/`). Pass the absolute
   task path to every child agent/tool; resume existing tasks in place; never fall back silently.
2. **All output and downloaded documents stay in the task folder** — documents under
   `step1_scope_and_research/references/<source>/`; run
   `uv run --project devtools --locked devtools/generate_sources_md.py <task> --organize` for `SOURCES.md` + manifest.
   Search the optional document root (`neqsim documents PATTERN`, read-only) and run
   `neqsim fetch-docs <task>` before calling a standard, datasheet or P&ID unavailable.
3. Standard/Comprehensive: `capability_assessment.md`, `analysis.md`, skill + agent discovery
   (`devtools/skill_search.py`, `devtools/agent_search.py`), benchmark validation against
   independent data, Monte Carlo P10/P50/P90 + tornado, ISO 31000 risk register,
   `devtools/consistency_checker.py` before the report.
4. `results.json` passes `TaskResultValidator` / `devtools/validate_task_results.py`, with
   `assumptions` and `data_gaps` always filled (schema: AGENT_REFERENCE.md Part A).
5. **Report:** `neqsim report <task>` (Word+HTML, `--pdf` optional). Title from `study.title`,
   language from `report.language`. Hand-written prose goes in
   `step3_report/report_sections.json` — **never open, edit or copy `generate_report.py`**
   (~320 KB). The report run also writes `WORK_RECORD.md`; fill its narrative blocks.
6. **Improve the tooling every task** (Java + JUnit for NeqSim gaps, `SKILL.md`/`*.agent.md`
   for tooling gaps), commit and push each fix to the repo that owns it, and record it in
   `neqsim_improvements.md` and `results.json` `improvements` (or state that nothing was needed).
   Never commit task output or company data to a code repo.
7. **Close the loop upstream.** Prefer the connected GitHub capability when available or required.
   Honor existing user/campaign authorization for issues and PRs; otherwise ask once whether
   to file it on `equinor/neqsim` now: `neqsim file-issue <task>` (`--list` to preview, `--pr` to
   also propose opening a PR for an already-implemented gap). Every issue and PR is confirmed
   interactively (or needs `--yes`) and `--dry-run` never calls `gh`/`git` — never file or push
   without authorization. `--yes` can reuse existing authorization. Remove private task data.
   Without a connector, no `gh`/no local checkout (the common case for plugin
   users) falls back to a pre-filled `github.com/.../issues/new` link opened in a browser —
   a form is not a filed issue. Details: `neqsim-task-workflow` §6.4.
8. Close with a privacy-safe `docs/development/TASK_LOG.md` entry.

Quick tasks skip the ceremony: answer with units, assumptions and a validation note.

## Token budget (every call resends the whole context)

- Search before reading: grep/`grep_search` then read the matching range. Never read whole
  `TASK_LOG.md`, `AGENT_REFERENCE.md`, `generate_report.py` or large skills end to end.
- Load a skill only when its topic is in play; load `neqsim-task-workflow` sections per phase.
- Documents: metadata first (STID `get_doc_references()` gives titles/types), extract text/OCR
  in a script, write a small JSON summary, read that. Use `view_image` only on cropped regions.
- Keep tool output small (counts, `Select-Object -First`, no full JSON/notebook dumps).
- Bulk work goes to scripts or subagents that write files; read only their summaries.

## Documentation

Docs in `docs/` need YAML front matter (`title`, `description`; quote values with colons),
KaTeX `$...$` / `$$...$$` (never `\(` `\[`), no markdown inside HTML blocks, verified relative
links, and entries in `docs/REFERENCE_MANUAL_INDEX.md` plus the section `index.md`.
Full rules: AGENT_REFERENCE.md Part B.

## Continuous improvement

Improving the agents and skills used — and their hand-offs — is part of every task. Fix missing
patterns, API signatures and gotchas in the owning `SKILL.md`/`*.agent.md`, add cross-references
both ways, and keep site-specific detail in the enterprise repos.
