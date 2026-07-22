# NeqSim Copilot Skills

Skills are reusable knowledge packages that agents load automatically when relevant.
Each skill folder contains a `SKILL.md` file with verified patterns, rules, and domain knowledge.

This folder is the **core NeqSim workspace discovery layer**. Core NeqSim skills live here directly.
Installed community/private skills are canonical under `~/.neqsim/skills/` and should not be
committed here. For VS Code, `neqsim skill export <name> --target vscode` defaults to the user's private
prompts skills folder; use `--vscode-scope workspace` only when a maintainer intentionally wants a
generated workspace copy. PaperLab keeps its full canonical library under `neqsim-paperlab/skills/`;
only the `@paperlab` gateway's public skills are exported for VS Code by default.

> **Full documentation:** See the [Skills and Agents Guide](../../docs/integration/skills_guide.md)
> for the complete walkthrough — creating core, community, and private skills,
> installing community/private agents, the SKILL.md and agent.yaml formats,
> requirements checklist, CLI commands, and worked examples.

## How Skills Work

1. An agent receives a user request
2. It identifies which skills are relevant based on the task description
3. It loads the `SKILL.md` file(s) using `read_file` before generating any response
4. The skill provides domain-specific patterns, rules, and code templates

**Skills are read-only** — agents consume them but don't modify them.

## Discovering the right skill

Two complementary mechanisms exist:

1. **Semantic search (preferred for new tasks).** Run
   `python devtools/skill_search.py "<your task title>" --top 5`. The script
   ranks every SKILL.md by TF-IDF cosine similarity over the front-matter
   `description`. Add or sharpen a skill's `description` to improve recall.
2. **Keyword index (`skill-index.json`).** A curated short-list mapping
   common phrases ("ocr", "fuel gas", "wax inhibitor") to skills. Useful for
   well-known queries; **not** intended to enumerate every keyword. New
   skills do not need exhaustive keyword entries — rely on `skill_search.py`
   to surface them.

CI (`.github/workflows/skills_agents_lint.yml`) verifies that every skill has
valid YAML front-matter and that every entry in `skill-index.json` points to
a skill that exists.

For tool-neutral coding agents, use `neqsim skill export <name> --target generic`
and point the tool at `~/.neqsim/export/generic/manifest.json` or the generated
`~/.neqsim/export/generic/skills/` folders.

---

## Skill Index

| Skill                                              | Description                                                                                                                                   | Primary Agents                              |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| `neqsim-agent-handoff`                             | Structured schemas for passing results between agents in multi-agent pipelines                                                                | router, solve.task                          |
| `neqsim-api-patterns`                              | Core NeqSim API patterns — EOS selection, fluid creation, flash, property access, equipment setup                                             | All agents                                  |
| `neqsim-capability-map`                            | Structured inventory of NeqSim capabilities by engineering discipline, gap identification                                                     | capability.scout, solve.task                |
| `neqsim-ccs-hydrogen`                              | CO2 capture/transport/storage and hydrogen systems — phase behavior, impurity management, injection wells                                     | ccs.hydrogen                                |
| `neqsim-controllability-operability`               | Operability — operating envelope mapping, turndown, control-valve sizing (ISA-75), startup/shutdown sequencing, recycle stability             | control.system, process.model, solve.task   |
| `neqsim-distillation-design`                       | Distillation column design — solver selection, feed tray rules, convergence, internals sizing                                                 | process.model, solve.task                   |
| `neqsim-dynamic-simulation`                        | Dynamic/transient simulation — runTransient, PID controllers, transmitters, depressurization                                                  | control.system, safety.depressuring         |
| `neqsim-electrolyte-systems`                       | Electrolyte/brine chemistry — CPA electrolyte EOS, ions, scale risk, MEG/DEG injection                                                        | thermo.fluid, flow.assurance                |
| `neqsim-eos-regression`                            | EOS parameter regression — kij tuning, PVT matching (CME, CVD), C7+ characterization                                                          | pvt.simulation, thermo.fluid                |
| `neqsim-equipment-cost-estimation`                 | Equipment-level CAPEX — Turton/Peters/Ulrich correlations, CEPCI escalation, AACE class, bare-module → grass-roots stackup                    | solve.task, mechanical.design, field.development |
| `neqsim-field-development`                         | Field development workflows — concept selection, tieback analysis, production forecasting, lifecycle management                               | field.development                           |
| `neqsim-field-economics`                           | Oil & gas economics — NPV, IRR, cash flow, tax regimes (Norwegian NCS, UK), cost estimation, Monte Carlo                                      | field.development, solve.task               |
| `neqsim-flow-assurance`                            | Flow assurance — hydrate, wax, asphaltene, corrosion, pipeline hydraulics, inhibitor dosing                                                   | flow.assurance                              |
| `neqsim-water-hammer`                              | Water/liquid hammer screening — fast valve closure, pump trip, STID route geometry, tagreader event windows, MCP runWaterHammer               | flow.assurance, process.model, plant.data   |
| `neqsim-heat-integration`                          | Pinch analysis & HEN — composite curves, ΔTmin, MER, grand composite, retrofit diagnostics                                                    | process.model, solve.task                   |
| `neqsim-input-validation`                          | Pre-simulation input validation — T, P, composition checks, component name verification                                                       | All simulation agents                       |
| `neqsim-java8-rules`                               | Java 8 compatibility rules — forbidden Java 9+ features, replacement patterns, JavaDoc requirements                                           | neqsim.test, All Java-writing agents        |
| `neqsim-notebook-patterns`                         | Jupyter notebook patterns — dual-boot setup, class imports, structure, visualization, results.json                                            | notebook.example, solve.task, solve.process |
| `neqsim-optimization-and-doe`                      | Process flowsheet optimization & DoE — SQP, Nelder-Mead, Particle Swarm, Pareto, Monte Carlo, BatchStudy, SciPy/Pyomo bridge                  | optimize, solve.task, process.model         |
| `neqsim-pdf-ocr`                                   | OCR text extraction from scanned PDFs and P&IDs — OCRmyPDF, Tesseract, pytesseract, tag-pattern post-filtering                                | technical.reader, solve.task                |
| `neqsim-physics-explanations`                      | Plain-language explanations of engineering phenomena for educational context                                                                  | All agents (educational mode)               |
| `neqsim-plant-data`                                | Plant historian integration — tagreader API (PI/IP.21), tag mapping, digital twin loops, data quality                                         | plant.data                                  |
| `neqsim-pid-process-operations`                    | P&ID-to-NeqSim operations — symbol interpretation, process graph extraction, tagreader mapping, valve-action steady/dynamic studies           | technical.reader, extract.process, plant.data, process.model, control.system, solve.task |
| `neqsim-model-calibration-and-data-reconciliation` | Digital twin calibration and data reconciliation — bounded parameter tuning, steady-state windowing, residual diagnostics, validation metrics | plant.data, solve.task                      |
| `neqsim-power-generation`                          | Power generation — gas turbines, steam turbines, HRSG, combined cycle, heat integration                                                       | process.model, solve.task                   |
| `neqsim-process-extraction`                        | Extract process data from text/tables/PFDs into NeqSim JSON builder format                                                                    | extract.process                             |
| `neqsim-process-safety`                            | Process safety — HAZOP guidewords, LOPA worksheets, SIL determination (IEC 61508/61511), bow-tie, risk matrix                                 | solve.task, safety.depressuring             |
| `neqsim-production-optimization`                   | Production optimization — decline curves, bottleneck analysis, gas lift, network optimization                                                 | field.development, solve.task               |
| `neqsim-professional-reporting`                    | Deliverable quality — `results.json` schema, traceability, KaTeX math, citations, uncertainty disclosure                                      | All agents                                  |
| `neqsim-reaction-engineering`                      | Chemical reactor patterns — GibbsReactor, PFR, CSTR, kinetics, AnaerobicDigester, bioprocessing                                               | reaction.engineering                        |
| `neqsim-regression-baselines`                      | Regression baseline management — creating fixtures, regression tests, detecting accuracy drift                                                | neqsim.test                                 |
| `neqsim-relief-flare-network`                      | Relief & flare design — PSV sizing API 520/521, fire heat input, flare radiation API 537, header back-pressure & Mach                         | safety.depressuring, mechanical.design      |
| `neqsim-standards-lookup`                          | Industry standards lookup — equipment-to-standards mapping, CSV database queries, compliance tracking                                         | mechanical.design, solve.task, gas.quality  |
| `neqsim-stid-retriever`                            | Engineering document retrieval — local dirs, manual upload, pluggable backends, relevance filtering                                           | technical.reader, solve.task                |
| `neqsim-subsea-and-wells`                          | Subsea systems and well design — SURF cost estimation, casing design, tieback analysis                                                        | field.development, mechanical.design        |
| `neqsim-technical-document-reading`                | Technical document reading — PDF/Word/Excel extraction, P&ID topology, vendor datasheets, image analysis                                      | technical.reader                            |
| `neqsim-troubleshooting`                           | Troubleshooting playbook — flash non-convergence, recycle divergence, zero values, phase ID issues                                            | All simulation agents                       |
| `neqsim-unisim-reader`                             | UniSim/HYSYS conversion — COM reader, component/EOS mapping, topology reconstruction, sub-flowsheets                                          | unisim.reader                               |
| `neqsim-utilities-specification`                   | Utilities — steam HP/MP/LP, cooling water, instrument air, fuel gas, N₂, demin, refrigeration sizing & specs                                  | process.model, solve.task                   |

---

## How to Contribute a Skill

Contributing a skill is the **easiest way to improve NeqSim's agentic system**.
You don't need to write Java — skills are markdown files with domain knowledge,
code patterns, and troubleshooting guidance that AI agents use to solve
engineering tasks better.

### Who should contribute skills?

- **Petroleum engineers** — share workflows for PVT, flow assurance, reservoir
- **Process engineers** — share equipment design patterns, operating procedures
- **Researchers** — share thermodynamic model guidance, validation approaches
- **Anyone** who has solved a NeqSim task and wants to save others time

### Step-by-step

> **Want to see a finished example first?** Read
> [`neqsim-input-validation/SKILL.md`](neqsim-input-validation/SKILL.md) — it's
> ~140 lines, well-structured, and shows the repeating "table → corrective action →
> code" pattern that most skills follow. A good skill to model yours after.

**1. Scaffold the skill:**

```bash
neqsim new-skill "my-topic"
neqsim new-skill "my-topic" --description "Short description of what it covers"
```

This creates `.github/skills/neqsim-my-topic/SKILL.md` with a structured
template including all required sections.

**2. Fill in the template:**

Edit the generated `SKILL.md`. The most important sections are:

- **When to Use** — trigger conditions so agents know when to load this skill
- **Code Patterns** — copy-paste Java/Python code that works with NeqSim's API
- **Common Mistakes** — what goes wrong and how to fix it

**3. Verify your code patterns:**

Every code example must work against NeqSim's actual API. Test by:
- Running the Java code as a JUnit test
- Running the Python code in a notebook with `from neqsim import jneqsim`
- Checking method names exist: `grep_search` or `file_search` in the source

**4. Register the skill:**

- Add an entry to the **Skill Index** table in this README
- Add a `<skill>` entry in `.github/copilot-instructions.md` under the `<skills>` section
- Add a row to the **Skills Reference** table in `AGENTS.md`

**5. Submit a PR:**

- One skill per PR
- Title: `[Skill] Add neqsim-my-topic skill`
- AI-assisted PRs are welcome — mark as `[AI-Assisted]` in the title

### What makes a great skill?

| Quality                     | Example                                                        |
| --------------------------- | -------------------------------------------------------------- |
| Specific trigger conditions | "USE WHEN: predicting hydrate formation temperature"           |
| Tested code patterns        | Java 8 code that compiles and runs against NeqSim API          |
| Real engineering context    | "Wire mesh demisters have K-factor 0.107 m/s per NORSOK"       |
| Common mistakes with fixes  | "Calling getViscosity() without initProperties() returns zero" |
| Reference to standards      | "Per API 521 Section 5.2, fire case heat flux is..."           |

### When to contribute to core vs. keep personal

See `VISION_AGENTS.md` for the full policy. Quick rule:

- **Core skill** — references NeqSim Java classes, useful to multiple users, verified
- **Community skill** — public, reusable workflow guidance that does not need to live in core
- **Private skill** — company-specific workflow, plant data, internal standards, private URLs, or project-specific knowledge

### Publishing a community skill (hosted in your own repo)

If your skill doesn't belong in core, you can publish it to the community catalog
with a single command:

```bash
neqsim skill publish your-username/neqsim-my-skill
```

This validates your `SKILL.md`, auto-generates a catalog entry, and opens a draft
PR to add it to `community-skills.yaml`. Others can then install it with:

```bash
neqsim skill install neqsim-my-skill
```

For public multi-skill repositories, add the repo once under `repositories:` in
`community-skills.yaml`. The skill CLI reads the online repo catalog and falls
back to scanning matching `SKILL.md` files, so `neqsim skill list` can show all
skills from that repo without one NeqSim catalog entry per skill.

The recommended public collection is
[equinor/neqsim-community-skills](https://github.com/equinor/neqsim-community-skills).
Use it for public, reproducible skills such as educational screening workflows,
open validation helpers, public engineering checklists, and examples based on
synthetic or public data. Do not put proprietary methods, plant data, tag names,
internal URLs, company standards, or project-specific design bases there; those
belong in a private skill catalog.

### Using private enterprise skills and agents

Company-private skill and agent repositories should be connected through each
user's local NeqSim catalogs, not through the public `neqsim` repository. This
keeps proprietary workflows, internal URLs, tag mappings, and company standards
out of committed files while still making them installable with the same CLI as
community skills and agents.

Create the local catalogs first:

```powershell
neqsim skill private-init
neqsim agent private-init
```

Then edit `%USERPROFILE%\.neqsim\private-skills.yaml` and add the private skill
repository under the generated `repositories:` section:

| Field | Value |
|-------|-------|
| `repo` | `your-org/your-enterprise-skills-repo` |
| `source` | `github` |
| `branch` | `main` |
| `catalog_path` | `community-skills.yaml` |
| `skill_path_glob` | `skills/**/SKILL.md` |
| `tags` | `[enterprise, private]` |
| `name_prefix` | Optional, for example `enterprise-`, to avoid public/private name clashes |

Edit `%USERPROFILE%\.neqsim\private-agents.yaml` the same way for private agent
repositories:

| Field | Value |
|-------|-------|
| `repo` | `your-org/your-enterprise-agents-repo` |
| `source` | `github` |
| `branch` | `main` |
| `catalog_path` | `community-agents.yaml` |
| `agent_path_glob` | `["agents/**/*.agent.md", "agents/**/AGENT.md"]` |
| `tags` | `[enterprise, private]` |
| `name_prefix` | Optional, for example `enterprise-`, to avoid public/private name clashes |

Private GitHub repositories should use a normal browser SSO session first, for
example `gh auth login --web`, or Git Credential Manager through your standard
enterprise `git clone` flow. `GITHUB_TOKEN`/`GH_TOKEN` and
`PRIVATE_SKILL_TOKEN`/`PRIVATE_AGENT_TOKEN` are non-interactive fallbacks for CI,
service contexts, or internal URL endpoints that cannot use the normal browser or
git credential flow. Do not store tokens in catalog files, prompt files, or this
repository. Use `catalog_path: ""` when the private repository has no
`community-skills.yaml` or `community-agents.yaml` and should be scanned directly
for `SKILL.md`, `AGENT.md`, or `*.agent.md` files.
After adding the catalog entries, install and verify locally:

```powershell
neqsim skill list --private
neqsim skill install <skill-name>

neqsim agent list --private
neqsim agent install <agent-name>
neqsim agent install --all
neqsim agent validate <agent-name>
```

Installed private skills are copied to
`%USERPROFILE%\.neqsim\skills\<skill-name>\SKILL.md`. VS Code Copilot does not
automatically discover that canonical folder, so export a generated copy to the
VS Code user prompts skills folder when you want VS Code to discover it:

```powershell
neqsim skill export <skill-name> --target vscode
neqsim skill doctor
```

Private agents install to `%USERPROFILE%\.neqsim\agents\<agent-name>\`. Export
them to the VS Code user prompts folder or the generic manifest layout for the AI
tool you use:

```powershell
neqsim agent export <agent-name> --target vscode
neqsim agent doctor --target vscode
neqsim agent export <agent-name> --target generic
neqsim agent doctor --target generic
```

### List existing skills

```bash
neqsim new-skill --list                   # core skills (in-repo)
neqsim skill list                          # community skills (catalog)
```

---

## Creating a New Skill (Quick Reference)

1. Run `neqsim new-skill "name"` to scaffold
2. Edit `.github/skills/neqsim-<name>/SKILL.md`
3. Test all code patterns against the actual API
4. Register in `copilot-instructions.md`, `AGENTS.md`, and this README
5. Submit PR with `[Skill]` prefix

### Skill File Format

Skills use YAML frontmatter + structured markdown:

```markdown
---
name: neqsim-my-topic
description: "Short description. USE WHEN: trigger condition."
last_verified: "2026-04-18"
---

# NeqSim My Topic

Summary of what this skill covers.

## When to Use This Skill

- When the user asks about X
- When a task involves Y

## Key Concepts

Brief domain explanation.

## NeqSim Code Patterns

### Pattern: Basic Setup

\```java
// Java 8 compatible
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
\```

## Common Mistakes

| Mistake                 | Fix                                     |
| ----------------------- | --------------------------------------- |
| Forgot initProperties() | Call fluid.initProperties() after flash |

## Validation Checklist

- [ ] Code compiles with Java 8
- [ ] Results validated against reference data

## References

- Standard or paper reference
```

---

## Cross-Reference Verification

Run the CI cross-reference script to verify all skill references in agent files
resolve to actual skill folders:

```bash
python devtools/verify_agent_skill_refs.py
```

This checks that:
- Every skill name referenced in agent files has a matching folder here
- Every skill folder here is referenced by at least one agent
- Every skill folder contains a `SKILL.md` file
